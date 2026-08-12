using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json.Serialization;
using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Tests.Support;
using Microsoft.AspNetCore.Builder;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace Calciolari.DataHub.Tests;

public sealed class ApiIntegrationTests : IAsyncLifetime
{
    private WebApplication _app = null!;
    private HttpClient _client = null!;
    private string _rawRoot = null!;

    public async Task InitializeAsync()
    {
        _rawRoot = Directory.CreateTempSubdirectory("datahub-it-raw").FullName;
        Environment.SetEnvironmentVariable("ASPNETCORE_ENVIRONMENT", "Development");
        Environment.SetEnvironmentVariable("DATAHUB_CONNECTION_STRING",
            "Host=127.0.0.1;Port=5432;Database=datahub;Username=datahub;Password=change-me");
        Environment.SetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT", _rawRoot);
        Environment.SetEnvironmentVariable("DATAHUB_SECURITY_ENABLED", "false");
        Environment.SetEnvironmentVariable("DATAHUB_SECURITY_USERS", "");
        Environment.SetEnvironmentVariable("DATAHUB_CORS_ALLOWED_ORIGINS", "http://localhost:5173");
        _app = AppHost.Create(["--urls=http://127.0.0.1:0"]);
        await _app.StartAsync();
        _client = new HttpClient { BaseAddress = new Uri(_app.Urls.Single()) };
        Clean();
    }

    public async Task DisposeAsync()
    {
        _client.Dispose();
        await _app.StopAsync();
        await _app.DisposeAsync();
    }

    private void Clean()
    {
        using var scope = _app.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<DataHubDbContext>();
        db.Database.ExecuteSqlRaw("""
            TRUNCATE TABLE
              sale_item, sale, product, validation_result, parsed_movement,
              artifact_publication, import_file, parse_attempt, import_job, raw_artifact
            RESTART IDENTITY CASCADE
            """);
        if (Directory.Exists(_rawRoot))
        {
            foreach (var entry in Directory.EnumerateFileSystemEntries(_rawRoot))
            {
                if (Directory.Exists(entry))
                {
                    Directory.Delete(entry, true);
                }
                else
                {
                    File.Delete(entry);
                }
            }
        }
    }

    [Fact]
    public async Task Health_and_info_are_public()
    {
        var health = await _client.GetAsync("/actuator/health");
        health.EnsureSuccessStatusCode();
        var body = await health.Content.ReadAsStringAsync();
        Assert.Contains("UP", body);
        Assert.Equal(HttpStatusCode.OK, (await _client.GetAsync("/actuator/health/liveness")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await _client.GetAsync("/actuator/health/readiness")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await _client.GetAsync("/actuator/info")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await _client.GetAsync("/actuator/metrics")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await _client.GetAsync("/nope")).StatusCode);
    }

    [Fact]
    public async Task Empty_dashboard_has_null_average_ticket()
    {
        Clean();
        var emptyDash = await _client.GetFromJsonAsync<JsonDashboard>("/api/dashboard");
        Assert.Equal(0, emptyDash!.SalesCount);
        Assert.Null(emptyDash.AverageTicket);
    }

    [Fact]
    public async Task Upload_rejects_invalid_transport()
    {
        var empty = new MultipartFormDataContent();
        var missing = await _client.PostAsync("/api/imports/qrp", empty);
        Assert.Equal(HttpStatusCode.BadRequest, missing.StatusCode);

        using var tooBigName = BuildFiles(("x.txt", "abc"u8.ToArray()));
        var ext = await _client.PostAsync("/api/imports/qrp", tooBigName);
        Assert.Equal(HttpStatusCode.BadRequest, ext.StatusCode);

        using var emptyFile = BuildFiles(("x.qrp", []));
        var emptyResp = await _client.PostAsync("/api/imports/qrp", emptyFile);
        Assert.Equal(HttpStatusCode.BadRequest, emptyResp.StatusCode);
    }

    [Fact]
    public async Task Upload_fixture_a_then_query_and_dedup()
    {
        var bytes = FixturePackage.RequireBytes("fixture-a");
        using var form = BuildFiles(("AUDITORIA.QRP", bytes));
        var upload = await _client.PostAsync("/api/imports/qrp", form);
        Assert.Equal(HttpStatusCode.Accepted, upload.StatusCode);
        Assert.StartsWith("/api/imports/", upload.Headers.Location!.ToString());
        var accepted = await upload.Content.ReadFromJsonAsync<JsonJob>();
        Assert.Equal("SUCCEEDED", accepted!.Status);
        Assert.False(accepted.Files[0].Deduplicated);

        var job = await _client.GetFromJsonAsync<JsonJob>("/api/imports/" + accepted.Id);
        Assert.Equal("SUCCEEDED", job!.Status);
        var file = await _client.GetFromJsonAsync<JsonFile>($"/api/imports/{accepted.Id}/files/{accepted.Files[0].Id}");
        Assert.Equal("IMPORTED", file!.Status);
        Assert.NotNull(file.Validations);
        Assert.NotEmpty(file.Validations);

        using var dup = BuildFiles(("OTHER.QRP", bytes));
        var dupResp = await _client.PostAsync("/api/imports/qrp", dup);
        dupResp.EnsureSuccessStatusCode();
        var dupJob = await dupResp.Content.ReadFromJsonAsync<JsonJob>();
        Assert.True(dupJob!.Files[0].Deduplicated);

        var products = await _client.GetFromJsonAsync<JsonPage<JsonProduct>>("/api/products?q=NHOQUE&page=0&size=20");
        Assert.True(products!.TotalElements >= 1);
        var product = await _client.GetFromJsonAsync<JsonProduct>("/api/products/" + products.Content[0].Id);
        Assert.Equal("35", product!.ExternalId);

        var sales = await _client.GetFromJsonAsync<JsonPage<JsonSale>>("/api/sales?page=0&size=20");
        Assert.True(sales!.TotalElements >= 1);
        var sale = await _client.GetFromJsonAsync<JsonSaleDetail>("/api/sales/" + sales.Content[0].Id);
        Assert.NotEmpty(sale!.Items);

        var dashboard = await _client.GetFromJsonAsync<JsonDashboard>("/api/dashboard");
        Assert.True(dashboard!.SalesCount >= 1);

        var productId = products.Content[0].Id;
        var filteredSalesResp = await _client.GetAsync(
            $"/api/sales?productId={productId}&from=2020-01-01T00:00:00&to=2099-12-31T23:59:59&page=0&size=20");
        var filteredSalesBody = await filteredSalesResp.Content.ReadAsStringAsync();
        Assert.True(filteredSalesResp.IsSuccessStatusCode, filteredSalesBody);
        var filteredDashResp = await _client.GetAsync(
            $"/api/dashboard?productId={productId}&from=2020-01-01T00:00:00&to=2099-12-31T23:59:59");
        var filteredDashBody = await filteredDashResp.Content.ReadAsStringAsync();
        Assert.True(filteredDashResp.IsSuccessStatusCode, filteredDashBody);

        var otherJob = await _client.GetFromJsonAsync<JsonPage<JsonJob>>("/api/imports?page=0&size=1");
        Assert.Equal(HttpStatusCode.NotFound,
            (await _client.GetAsync($"/api/imports/{Guid.NewGuid()}/files/{accepted.Files[0].Id}")).StatusCode);

        var list = await _client.GetFromJsonAsync<JsonPage<JsonJob>>("/api/imports?page=0&size=20");
        Assert.True(list!.TotalElements >= 1);

        var reprocess = await _client.PostAsync($"/api/imports/files/{accepted.Files[0].Id}/reprocess", null);
        reprocess.EnsureSuccessStatusCode();

        Assert.Equal(HttpStatusCode.NotFound, (await _client.GetAsync("/api/imports/" + Guid.NewGuid())).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await _client.GetAsync("/api/products/" + Guid.NewGuid())).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await _client.GetAsync("/api/sales/" + Guid.NewGuid())).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await _client.GetAsync("/api/imports?page=-1")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await _client.GetAsync("/api/imports?size=0")).StatusCode);
    }

    [Fact]
    public async Task Garbage_qrp_is_stored_but_not_published()
    {
        var garbage = Enumerable.Range(0, 64).Select(i => (byte)i).ToArray();
        using var form = BuildFiles(("noise.qrp", garbage));
        var upload = await _client.PostAsync("/api/imports/qrp", form);
        Assert.Equal(HttpStatusCode.Accepted, upload.StatusCode);
        var job = await upload.Content.ReadFromJsonAsync<JsonJob>();
        Assert.Equal("FAILED", job!.Status);
        Assert.Equal("FAILED", job.Files[0].Status);
        var products = await _client.GetFromJsonAsync<JsonPage<JsonProduct>>("/api/products");
        Assert.Equal(0, products!.TotalElements);
    }

    [Fact]
    public async Task Partial_success_mixed_batch()
    {
        var good = FixturePackage.RequireBytes("fixture-a");
        var bad = new byte[] { 1, 2, 3 };
        using var form = BuildFiles(("AUDITORIA.QRP", good), ("bad.qrp", bad));
        var upload = await _client.PostAsync("/api/imports/qrp", form);
        var job = await upload.Content.ReadFromJsonAsync<JsonJob>();
        Assert.Equal("PARTIAL_SUCCESS", job!.Status);
    }

    private static MultipartFormDataContent BuildFiles(params (string Name, byte[] Bytes)[] files)
    {
        var form = new MultipartFormDataContent();
        foreach (var file in files)
        {
            var content = new ByteArrayContent(file.Bytes);
            content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
            form.Add(content, "files", file.Name);
        }

        return form;
    }

    private sealed record JsonJob(Guid JobId, Guid Id, string Status, List<JsonFile> Files);
    private sealed record JsonFile(Guid Id, string OriginalFilename, string Status, bool Deduplicated, List<JsonValidation>? Validations);
    private sealed record JsonValidation(string Code, string Status);
    private sealed record JsonPage<T>(List<T> Content, int Page, int Size, long TotalElements, int TotalPages);
    private sealed record JsonProduct(Guid Id, string ExternalId, string Name);
    private sealed record JsonSale(Guid Id, string ExternalSaleId);
    private sealed record JsonSaleDetail(Guid Id, List<JsonSaleItem> Items);
    private sealed record JsonSaleItem(Guid Id, string ProductName);
    private sealed record JsonDashboard(long SalesCount, string RevenueTotal, string? AverageTicket);
}

public sealed class SecurityIntegrationTests : IAsyncLifetime
{
    private WebApplication _app = null!;
    private HttpClient _client = null!;

    public async Task InitializeAsync()
    {
        Environment.SetEnvironmentVariable("ASPNETCORE_ENVIRONMENT", "Development");
        Environment.SetEnvironmentVariable("DATAHUB_CONNECTION_STRING",
            "Host=127.0.0.1;Port=5432;Database=datahub;Username=datahub;Password=change-me");
        Environment.SetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT", Directory.CreateTempSubdirectory("sec-raw").FullName);
        Environment.SetEnvironmentVariable("DATAHUB_SECURITY_ENABLED", "true");
        Environment.SetEnvironmentVariable("DATAHUB_SECURITY_USERS",
            "viewer:v:VIEWER,importer:i:IMPORTER|VIEWER,admin:a:ADMIN|IMPORTER|VIEWER");
        Environment.SetEnvironmentVariable("DATAHUB_CORS_ALLOWED_ORIGINS", "");
        _app = AppHost.Create(["--urls=http://127.0.0.1:0"]);
        await _app.StartAsync();
        _client = new HttpClient { BaseAddress = new Uri(_app.Urls.Single()) };
    }

    public async Task DisposeAsync()
    {
        _client.Dispose();
        await _app.StopAsync();
        await _app.DisposeAsync();
        Environment.SetEnvironmentVariable("DATAHUB_SECURITY_ENABLED", "false");
        Environment.SetEnvironmentVariable("DATAHUB_SECURITY_USERS", "");
    }

    [Fact]
    public async Task Auth_enforced()
    {
        Assert.Equal(HttpStatusCode.OK, (await _client.GetAsync("/actuator/health")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await _client.GetAsync("/api/products")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await _client.GetAsync("/actuator/metrics")).StatusCode);

        using var viewer = Authed("viewer", "v");
        Assert.Equal(HttpStatusCode.OK, (await viewer.GetAsync("/api/products")).StatusCode);
        using var upload = new MultipartFormDataContent();
        upload.Add(new ByteArrayContent("x"u8.ToArray()), "files", "x.qrp");
        Assert.Equal(HttpStatusCode.Forbidden, (await viewer.PostAsync("/api/imports/qrp", upload)).StatusCode);

        using var importer = Authed("importer", "i");
        Assert.Equal(HttpStatusCode.Forbidden, (await importer.PostAsync("/api/imports/files/" + Guid.NewGuid() + "/reprocess", null)).StatusCode);

        using var admin = Authed("admin", "a");
        Assert.Equal(HttpStatusCode.OK, (await admin.GetAsync("/actuator/metrics")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await admin.PostAsync("/api/imports/files/" + Guid.NewGuid() + "/reprocess", null)).StatusCode);

        using var bad = Authed("viewer", "wrong");
        Assert.Equal(HttpStatusCode.Unauthorized, (await bad.GetAsync("/api/products")).StatusCode);

        using var unknown = Authed("nobody", "v");
        Assert.Equal(HttpStatusCode.Unauthorized, (await unknown.GetAsync("/api/products")).StatusCode);

        using var bearer = new HttpClient { BaseAddress = _client.BaseAddress };
        bearer.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "nope");
        Assert.Equal(HttpStatusCode.Unauthorized, (await bearer.GetAsync("/api/products")).StatusCode);

        using var garbage = new HttpClient { BaseAddress = _client.BaseAddress };
        garbage.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", "%%%");
        Assert.Equal(HttpStatusCode.Unauthorized, (await garbage.GetAsync("/api/products")).StatusCode);

        using var noColon = new HttpClient { BaseAddress = _client.BaseAddress };
        noColon.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Basic", Convert.ToBase64String(Encoding.UTF8.GetBytes("nocolon")));
        Assert.Equal(HttpStatusCode.Unauthorized, (await noColon.GetAsync("/api/products")).StatusCode);
    }

    private HttpClient Authed(string user, string pass)
    {
        var client = new HttpClient { BaseAddress = _client.BaseAddress };
        var token = Convert.ToBase64String(Encoding.UTF8.GetBytes(user + ":" + pass));
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", token);
        return client;
    }
}
