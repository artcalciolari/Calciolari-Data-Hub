using Calciolari.DataHub.Imports.Domain.Hints;
using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Shared.Api;
using Calciolari.DataHub.Shared.Provenance;
using Microsoft.AspNetCore.Http;

namespace Calciolari.DataHub.Tests;

public sealed class DomainUnitTests
{
    [Fact]
    public void Provenance_wire_names_and_invalid()
    {
        Assert.Equal("SOURCE_DATA", ProvenanceKind.SourceData.WireName());
        Assert.Equal("CALCULATED_DATA", ProvenanceKind.CalculatedData.WireName());
        Assert.Equal("INFERRED_DATA", ProvenanceKind.InferredData.WireName());
        Assert.Throws<ArgumentOutOfRangeException>(() => ((ProvenanceKind)99).WireName());
    }

    [Fact]
    public void Movement_direction_wire_names()
    {
        Assert.Equal("OUT", MovementDirection.Out.WireName());
        Assert.Equal("IN", MovementDirection.In.WireName());
        Assert.Equal("RETURN", MovementDirection.Return.WireName());
        Assert.Equal("UNKNOWN", MovementDirection.Unknown.WireName());
        Assert.Throws<ArgumentOutOfRangeException>(() => ((MovementDirection)99).WireName());
    }

    [Fact]
    public void SourceLocator_empty_and_toString()
    {
        Assert.Equal("SourceLocator[page=null, recordIndex=null, byteOffset=null, detail=null]", SourceLocator.Empty.ToString());
        Assert.Equal("SourceLocator[page=1, recordIndex=2, byteOffset=3, detail=y=9]", new SourceLocator(1, 2, 3, "y=9").ToString());
    }

    [Fact]
    public void ParseIssue_create_defaults_locator()
    {
        var issue = ParseIssue.Create("X", IssueSeverity.Info, IssueStage.Layout, null, "msg");
        Assert.Equal(SourceLocator.Empty, issue.SourceLocator);
        var located = ParseIssue.Create(
            "Y", IssueSeverity.Warning, IssueStage.Layout, new SourceLocator(1, 2, 3, "d"), "located");
        Assert.Equal(1, located.SourceLocator.Page);
        Assert.Throws<ArgumentNullException>(() => ParseIssue.Create(null!, IssueSeverity.Info, IssueStage.Layout, null, "m"));
        Assert.Throws<ArgumentNullException>(() => ParseIssue.Create("X", IssueSeverity.Info, IssueStage.Layout, null, null!));
    }

    [Fact]
    public void ParsedImport_detects_fatal_and_error()
    {
        var ok = new ParsedImport("S", "p", "v", null, null, [], ParsedImportTotals.Empty, ParsedImportStats.Empty, []);
        Assert.False(ok.HasFatalOrError());
        var fatal = ok with { Issues = [new ParseIssue("F", IssueSeverity.Fatal, IssueStage.Container, SourceLocator.Empty, "x")] };
        Assert.True(fatal.HasFatalOrError());
        var error = ok with { Issues = [new ParseIssue("E", IssueSeverity.Error, IssueStage.Layout, SourceLocator.Empty, "x")] };
        Assert.True(error.HasFatalOrError());
    }

    [Fact]
    public void ParserInput_create_validates()
    {
        using var stream = new MemoryStream();
        var input = ParserInput.Create(stream, 0, "  ", "QRP");
        Assert.Null(input.OriginalFilenameOptional());
        Assert.Equal("a.qrp", ParserInput.Create(stream, 1, "a.qrp", null).OriginalFilenameOptional());
        Assert.Throws<ArgumentNullException>(() => ParserInput.Create(null!, 0, null, null));
        Assert.Throws<ArgumentOutOfRangeException>(() => ParserInput.Create(stream, -1, null, null));
    }

    [Fact]
    public void IncompleteDate_create_and_year()
    {
        var d = IncompleteDate.Create(1, 7);
        Assert.False(d.HasYear);
        Assert.True(IncompleteDate.Create(1, 7, 2026).HasYear);
        Assert.Throws<ArgumentOutOfRangeException>(() => IncompleteDate.Create(0, 1));
        Assert.Throws<ArgumentOutOfRangeException>(() => IncompleteDate.Create(32, 1));
        Assert.Throws<ArgumentOutOfRangeException>(() => IncompleteDate.Create(1, 0));
        Assert.Throws<ArgumentOutOfRangeException>(() => IncompleteDate.Create(1, 13));
    }

    [Fact]
    public void FilenameHints_empty()
    {
        Assert.True(FilenameHints.Empty(null).IsEmpty);
        Assert.Equal("", FilenameHints.Empty(null).OriginalFilename);
        Assert.False(new FilenameHints("x", new IncompleteDateRange(IncompleteDate.Create(1, 1), IncompleteDate.Create(2, 2)), null).IsEmpty);
        Assert.False(new FilenameHints("x", null, IncompleteDate.Create(1, 1)).IsEmpty);
    }

    [Fact]
    public void PageParams_and_page_response()
    {
        Assert.Equal(0, PageParams.Page(null));
        Assert.Equal(2, PageParams.Page(2));
        var ex = Assert.Throws<ApiException>(() => PageParams.Page(-1));
        Assert.Equal(400, ex.StatusCode);
        Assert.Equal(20, PageParams.Size(null));
        Assert.Equal(10, PageParams.Size(10));
        Assert.Throws<ApiException>(() => PageParams.Size(0));
        Assert.Throws<ApiException>(() => PageParams.Size(101));
        var page = PageResponse<int>.Of([1, 2], 0, 20, 2);
        Assert.Equal(1, page.TotalPages);
        Assert.Equal(0, PageResponse<int>.Of([], 0, 0, 5).TotalPages);
    }

    [Fact]
    public void Decimal_and_datetime_text()
    {
        Assert.Null(DecimalText.ToPlainString((decimal?)null));
        Assert.Equal("1.5", DecimalText.ToPlainString(1.5m));
        Assert.Equal("10.84", DecimalText.StripTrailingZerosToPlain(10.840m));
        Assert.Equal("10", DecimalText.StripTrailingZerosToPlain(10m));
        decimal? missing = null;
        Assert.Null(DecimalText.StripTrailingZerosToPlain(missing));
        decimal? present = 1.50m;
        Assert.Equal("1.5", DecimalText.StripTrailingZerosToPlain(present));
        Assert.Null(DecimalText.StripTrailingZerosToPlain((decimal?)null));
        Assert.Null(DateTimeText.Iso((DateTime?)null));
        Assert.Equal("2026-08-07T12:22:13", DateTimeText.Iso(new DateTime(2026, 8, 7, 12, 22, 13)));
    }

    [Fact]
    public void ApiException_preserves_inner()
    {
        var inner = new InvalidOperationException("x");
        var ex = new ApiException(409, "conflict", inner);
        Assert.Equal(409, ex.StatusCode);
        Assert.Same(inner, ex.InnerException);
    }

    [Fact]
    public void ExceptionHandling_describe_and_sanitize()
    {
        var api = ExceptionHandling.Describe(new ApiException(404, "missing"));
        Assert.Equal(404, api.Status);
        Assert.Equal("Not Found", api.Title);
        var arg = ExceptionHandling.Describe(new ArgumentException("bad"));
        Assert.Equal(400, arg.Status);
        var bad = ExceptionHandling.Describe(new BadHttpRequestException("The form field is missing"));
        Assert.Equal("Missing required part: files", bad.Detail);
        var otherBad = ExceptionHandling.Describe(new BadHttpRequestException("nope"));
        Assert.Equal("nope", otherBad.Detail);
        var unexpected = ExceptionHandling.Describe(new InvalidOperationException("boom"));
        Assert.Equal(500, unexpected.Status);
        Assert.Equal("UNEXPECTED", unexpected.Extra!["code"]);
        Assert.Equal("Request could not be processed", ExceptionHandling.Sanitize(null));
        Assert.Equal("Request could not be processed", ExceptionHandling.Sanitize("  "));
        Assert.Equal(500, ExceptionHandling.Sanitize(new string('a', 600)).Length);
        Assert.Equal("Unauthorized", ExceptionHandling.Describe(new ApiException(401, "x")).Title);
        Assert.Equal("Forbidden", ExceptionHandling.Describe(new ApiException(403, "x")).Title);
        Assert.Equal("Conflict", ExceptionHandling.Describe(new ApiException(409, "x")).Title);
        Assert.Equal("Payload Too Large", ExceptionHandling.Describe(new ApiException(413, "x")).Title);
        Assert.Equal("418", ExceptionHandling.Describe(new ApiException(418, "x")).Title);
    }

    [Fact]
    public async Task ExceptionHandling_writes_problem_details()
    {
        var boom = new DefaultHttpContext();
        boom.Response.Body = new MemoryStream();
        boom.Features.Set<Microsoft.AspNetCore.Diagnostics.IExceptionHandlerFeature>(
            new Microsoft.AspNetCore.Diagnostics.ExceptionHandlerFeature
            {
                Error = new InvalidOperationException("boom")
            });
        await ExceptionHandling.WriteExceptionProblemAsync(boom);
        boom.Response.Body.Position = 0;
        var json = await new StreamReader(boom.Response.Body).ReadToEndAsync();
        Assert.Contains("UNEXPECTED", json);

        var missingFeature = new DefaultHttpContext();
        missingFeature.Response.Body = new MemoryStream();
        await ExceptionHandling.WriteExceptionProblemAsync(missingFeature);
        Assert.Equal(500, missingFeature.Response.StatusCode);

        var skipLength = new DefaultHttpContext();
        skipLength.Response.StatusCode = 404;
        skipLength.Response.ContentLength = 4;
        skipLength.Response.Body = new MemoryStream();
        await ExceptionHandling.WriteStatusCodeProblemAsync(skipLength);
        Assert.Equal(0, skipLength.Response.Body.Length);

        var skipOk = new DefaultHttpContext();
        skipOk.Response.StatusCode = 200;
        skipOk.Response.Body = new MemoryStream();
        await ExceptionHandling.WriteStatusCodeProblemAsync(skipOk);
        Assert.Equal(0, skipOk.Response.Body.Length);

        var notFound = new DefaultHttpContext();
        notFound.Response.StatusCode = 404;
        notFound.Response.Body = new MemoryStream();
        await ExceptionHandling.WriteStatusCodeProblemAsync(notFound);
        notFound.Response.Body.Position = 0;
        Assert.Contains("Not Found", await new StreamReader(notFound.Response.Body).ReadToEndAsync());

        var other = new DefaultHttpContext();
        other.Response.StatusCode = 418;
        other.Response.Body = new MemoryStream();
        await ExceptionHandling.WriteStatusCodeProblemAsync(other);
        other.Response.Body.Position = 0;
        Assert.Contains("Error", await new StreamReader(other.Response.Body).ReadToEndAsync());
    }

    [Fact]
    public void Entities_and_health_helpers()
    {
        _ = new Calciolari.DataHub.Persistence.Entities.ValidationResultEntity();
        var product = new Calciolari.DataHub.Persistence.Entities.ProductEntity(
            Guid.NewGuid(), "INTERPDV", "1", "old", Guid.NewGuid());
        product.SetName("new");
        Assert.Equal("new", product.Name);

        _ = AppHost.HealthJson(false);
        _ = AppHost.HealthJson(true);
        var readyDown = AppHost.ReadinessJson(false);
        Assert.Equal(StatusCodes.Status503ServiceUnavailable, ((IStatusCodeHttpResult)readyDown).StatusCode);
        _ = AppHost.ReadinessJson(true);
    }

    [Fact]
    public void EmfPage_create_validates()
    {
        var page = Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp.EmfPage.Create(0, 10);
        Assert.Equal(10, page.EndExclusive);
        Assert.Throws<ArgumentOutOfRangeException>(() => Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp.EmfPage.Create(-1, 10));
        Assert.Throws<ArgumentOutOfRangeException>(() => Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp.EmfPage.Create(0, 0));
    }

    [Fact]
    public void RawFileDescriptor_create_validates()
    {
        var sha = new string('a', 64);
        var d = Calciolari.DataHub.Imports.Infrastructure.Storage.RawFileDescriptor.Create(sha, 1, "QRP");
        Assert.Equal(sha, d.Sha256);
        Assert.Throws<ArgumentNullException>(() => Calciolari.DataHub.Imports.Infrastructure.Storage.RawFileDescriptor.Create(null!, 1, null));
        Assert.Throws<ArgumentException>(() => Calciolari.DataHub.Imports.Infrastructure.Storage.RawFileDescriptor.Create("abc", 1, null));
        Assert.Throws<ArgumentOutOfRangeException>(() => Calciolari.DataHub.Imports.Infrastructure.Storage.RawFileDescriptor.Create(sha, -1, null));
    }
}
