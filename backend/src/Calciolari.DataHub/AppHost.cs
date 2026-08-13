using Calciolari.DataHub.Imports.Application;
using Calciolari.DataHub.Imports.Domain.Hints;
using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;
using Calciolari.DataHub.Imports.Infrastructure.Storage;
using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Shared.Api;
using Calciolari.DataHub.Shared.Security;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Cors.Infrastructure;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub;

public static class AppHost
{
    public static WebApplication Create(string[] args, Action<WebApplicationBuilder>? configure = null)
    {
        var builder = WebApplication.CreateBuilder(args);
        var options = BindOptions(builder.Configuration, builder.Environment.EnvironmentName);
        builder.Services.AddSingleton(options);

        builder.WebHost.ConfigureKestrel(kestrel =>
        {
            kestrel.Limits.MaxRequestBodySize = 64L * 1024 * 1024;
        });
        builder.Services.Configure<FormOptions>(form =>
        {
            form.MultipartBodyLengthLimit = 64L * 1024 * 1024;
            form.ValueLengthLimit = int.MaxValue;
        });

        builder.Services.AddDbContext<DataHubDbContext>(db =>
            db.UseNpgsql(options.ConnectionString));
        builder.Services.AddHealthChecks().AddDbContextCheck<DataHubDbContext>();

        builder.Services.AddSingleton<IRawFileStorage>(_ => new LocalRawFileStorage(options.RawStorageRoot));
        builder.Services.AddSingleton<IImportParser>(_ => new InterPdvQrpParser(
            new QrpContainerReader(),
            new EmfTextRecordExtractor(),
            new InterPdvReportLayoutMapper(),
            new InterPdvParsedImportValidator(),
            options.ParserMaxBytes));
        builder.Services.AddSingleton<FilenameHintsParser>();
        builder.Services.AddSingleton<IImportWorkQueue, ImportWorkQueue>();
        builder.Services.AddSingleton<ImportMetrics>();
        builder.Services.Configure<ImportWorkerOptions>(_ => { });
        builder.Services.AddScoped<ImportIngestionService>();
        builder.Services.AddScoped<IImportFileProcessor>(sp => sp.GetRequiredService<ImportIngestionService>());
        builder.Services.AddScoped<IRawStorageReconciler, RawStorageReconciler>();
        builder.Services.AddHostedService<ImportParseWorker>();
        builder.Services.AddScoped<ImportQueryService>();
        builder.Services.AddScoped<Catalog.Application.ProductQueryService>();
        builder.Services.AddScoped<Sales.Application.SaleQueryService>();
        builder.Services.AddScoped<Analytics.Application.DashboardQueryService>();

        builder.Services.AddControllers()
            .AddApplicationPart(typeof(AppHost).Assembly)
            .AddJsonOptions(json =>
            {
                json.JsonSerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
                json.JsonSerializerOptions.DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.Never;
            });
        builder.Services.AddProblemDetails();
        builder.Services.AddOpenApi();

        builder.Services.AddAuthentication(BasicAuthenticationHandler.SchemeName)
            .AddScheme<AuthenticationSchemeOptions, BasicAuthenticationHandler>(
                BasicAuthenticationHandler.SchemeName, _ => { });
        builder.Services.AddAuthorization(auth =>
        {
            auth.FallbackPolicy = options.SecurityEnabled
                ? new AuthorizationPolicyBuilder(BasicAuthenticationHandler.SchemeName)
                    .RequireAuthenticatedUser()
                    .Build()
                : null;
        });

        var origins = options.OriginList().ToArray();
        if (origins.Length > 0)
        {
            ConfigureCors(builder.Services, origins);
        }

        configure?.Invoke(builder);

        var app = builder.Build();
        options.ValidateOrThrow(app.Environment.EnvironmentName);

        using (var scope = app.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<DataHubDbContext>();
            SqlMigrator.Apply(db);
        }

        app.UseDataHubExceptionHandling();
        app.Use(async (context, next) =>
        {
            context.Response.Headers["X-Content-Type-Options"] = "nosniff";
            context.Response.Headers["X-Frame-Options"] = "DENY";
            context.Response.Headers["Referrer-Policy"] = "no-referrer";
            await next();
        });

        if (origins.Length > 0)
        {
            app.UseCors("datahub");
        }

        app.UseAuthentication();
        app.UseAuthorization();
        app.MapControllers();
        app.MapOpenApi().AllowAnonymous();

        app.MapGet("/actuator/health", async (DataHubDbContext db) =>
            HealthJson(await db.Database.CanConnectAsync())).AllowAnonymous();

        app.MapGet("/actuator/health/liveness", () => Results.Json(new { status = "UP" })).AllowAnonymous();
        app.MapGet("/actuator/health/readiness", async (DataHubDbContext db) =>
            ReadinessJson(await db.Database.CanConnectAsync())).AllowAnonymous();

        app.MapGet("/actuator/info", () => Results.Json(new
        {
            app = new { name = "datahub", version = "0.0.1" }
        })).AllowAnonymous();

        app.MapGet("/actuator/metrics", (ImportMetrics metrics) => Results.Json(metrics.Snapshot()))
            .RequireAuthorization(new AuthorizeAttribute { Roles = "ADMIN" });

        return app;
    }

    internal static IResult HealthJson(bool canConnect) =>
        canConnect
            ? Results.Json(new { status = "UP" })
            : Results.Json(new { status = "DOWN" }, statusCode: StatusCodes.Status503ServiceUnavailable);

    internal static IResult ReadinessJson(bool canConnect) =>
        canConnect
            ? Results.Json(new { status = "UP" })
            : Results.Json(new { status = "DOWN" }, statusCode: StatusCodes.Status503ServiceUnavailable);

    public static DataHubOptions BindOptions(IConfiguration configuration, string environmentName)
    {
        var options = new DataHubOptions();
        configuration.GetSection(DataHubOptions.SectionName).Bind(options);

        options.ConnectionString = FirstNonEmpty(
            Environment.GetEnvironmentVariable("DATAHUB_CONNECTION_STRING"),
            configuration["ConnectionStrings:DefaultConnection"],
            FromSpringJdbc(
                Environment.GetEnvironmentVariable("SPRING_DATASOURCE_URL"),
                Environment.GetEnvironmentVariable("SPRING_DATASOURCE_USERNAME"),
                Environment.GetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD")),
            options.ConnectionString)!;

        options.RawStorageRoot = FirstNonEmpty(
            Environment.GetEnvironmentVariable("DATAHUB_RAW_STORAGE_ROOT"),
            options.RawStorageRoot)!;

        if (int.TryParse(Environment.GetEnvironmentVariable("DATAHUB_IMPORTS_MAX_FILES"), out var maxFiles))
        {
            options.MaxFiles = maxFiles;
        }

        if (long.TryParse(Environment.GetEnvironmentVariable("DATAHUB_IMPORTS_MAX_FILE_BYTES"), out var maxBytes))
        {
            options.MaxFileBytes = maxBytes;
        }

        if (long.TryParse(Environment.GetEnvironmentVariable("DATAHUB_PARSER_MAX_BYTES"), out var parserMax))
        {
            options.ParserMaxBytes = parserMax;
        }

        var securityEnabled = Environment.GetEnvironmentVariable("DATAHUB_SECURITY_ENABLED");
        if (!string.IsNullOrWhiteSpace(securityEnabled))
        {
            options.SecurityEnabled = bool.Parse(securityEnabled);
        }

        var users = Environment.GetEnvironmentVariable("DATAHUB_SECURITY_USERS");
        if (!string.IsNullOrWhiteSpace(users))
        {
            options.SecurityUsers = users;
        }

        var cors = Environment.GetEnvironmentVariable("DATAHUB_CORS_ALLOWED_ORIGINS");
        if (cors is not null)
        {
            options.CorsAllowedOrigins = cors;
        }

        if (string.Equals(environmentName, "Production", StringComparison.OrdinalIgnoreCase))
        {
            options.SecurityEnabled = true;
            options.SecurityRequireEnabled = true;
            if (string.IsNullOrWhiteSpace(options.CorsAllowedOrigins))
            {
                options.CorsAllowedOrigins = string.Empty;
            }
        }

        return options;
    }

    internal static string? FromSpringJdbc(string? jdbcUrl, string? user, string? password)
    {
        if (string.IsNullOrWhiteSpace(jdbcUrl) || !jdbcUrl.StartsWith("jdbc:postgresql://", StringComparison.Ordinal))
        {
            return null;
        }

        var rest = jdbcUrl["jdbc:postgresql://".Length..];
        var slash = rest.IndexOf('/');
        var hostPort = slash >= 0 ? rest[..slash] : rest;
        var db = slash >= 0 ? rest[(slash + 1)..] : "datahub";
        var q = db.IndexOf('?');
        if (q >= 0)
        {
            db = db[..q];
        }

        var colon = hostPort.LastIndexOf(':');
        var host = colon >= 0 ? hostPort[..colon] : hostPort;
        var port = colon >= 0 ? hostPort[(colon + 1)..] : "5432";
        return $"Host={host};Port={port};Database={db};Username={user ?? "datahub"};Password={password ?? "change-me"}";
    }

    internal static void ConfigureCors(IServiceCollection services, string[] origins)
    {
        var policy = new CorsPolicyBuilder()
            .WithOrigins(origins)
            .AllowAnyHeader()
            .WithMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .WithExposedHeaders("Location")
            .AllowCredentials()
            .SetPreflightMaxAge(TimeSpan.FromSeconds(3600))
            .Build();
        services.AddCors();
        services.Configure<CorsOptions>(options =>
            options.AddPolicy("datahub", policy));
    }

    private static string? FirstNonEmpty(params string?[] values) =>
        values.FirstOrDefault(v => !string.IsNullOrWhiteSpace(v));
}
