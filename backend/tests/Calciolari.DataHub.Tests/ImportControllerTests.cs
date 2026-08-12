using Calciolari.DataHub.Imports.Api;
using Calciolari.DataHub.Shared.Api;
using Microsoft.AspNetCore.Http;

namespace Calciolari.DataHub.Tests;

public sealed class ImportControllerTests
{
    [Fact]
    public void Upload_validates_files_before_ingest()
    {
        var controller = new ImportController(null!, null!, new DataHubOptions { MaxFiles = 1, MaxFileBytes = 4 });
        var empty = Assert.Throws<ApiException>(() => controller.Upload(null));
        Assert.Equal(StatusCodes.Status400BadRequest, empty.StatusCode);
        Assert.Throws<ApiException>(() => controller.Upload([]));
        Assert.Throws<ApiException>(() => controller.Upload([Form("a.qrp", [1]), Form("b.qrp", [1])]));
        Assert.Throws<ApiException>(() => controller.Upload([Form("a.qrp", [])]));
        var tooBig = Assert.Throws<ApiException>(() => controller.Upload([Form("a.qrp", [1, 2, 3, 4, 5])]));
        Assert.Equal(StatusCodes.Status413PayloadTooLarge, tooBig.StatusCode);
        Assert.Throws<ApiException>(() => controller.Upload([Form("a.txt", [1])]));
        Assert.Throws<ApiException>(() => controller.Upload([new NullNameFile()]));
    }

    private sealed class NullNameFile : IFormFile
    {
        public string ContentType => "application/octet-stream";
        public string ContentDisposition => "";
        public IHeaderDictionary Headers { get; } = new HeaderDictionary();
        public long Length => 1;
        public string Name => "files";
        public string FileName => null!;
        public void CopyTo(Stream target) => target.WriteByte(1);
        public Task CopyToAsync(Stream target, CancellationToken cancellationToken = default)
        {
            target.WriteByte(1);
            return Task.CompletedTask;
        }
        public Stream OpenReadStream() => new MemoryStream([1]);
    }

    private static FormFile Form(string name, byte[] bytes)
    {
        var stream = new MemoryStream(bytes);
        return new FormFile(stream, 0, bytes.Length, "files", name);
    }
}
