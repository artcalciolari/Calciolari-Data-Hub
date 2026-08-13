using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;
using Calciolari.DataHub.Tests.Support;

namespace Calciolari.DataHub.Tests;

public sealed class ArchitectureAndFuzzTests
{
    [Fact]
    public void Bounded_contexts_do_not_reference_qrp_parser_types()
    {
        var src = FindSourceRoot();
        foreach (var folder in new[] { "Catalog", "Sales", "Analytics", "Persistence", "Shared", "NoSuchBoundedContext" })
        {
            var dir = Path.Combine(src, folder);
            if (!Directory.Exists(dir))
            {
                continue;
            }

            foreach (var file in Directory.GetFiles(dir, "*.cs", SearchOption.AllDirectories))
            {
                var text = File.ReadAllText(file);
                Assert.DoesNotContain("Imports.Infrastructure.InterPdv.Qrp", text);
            }
        }
    }

    [Fact]
    public void Random_and_truncated_bytes_do_not_crash_parser()
    {
        var parser = new InterPdvQrpParser();
        var rng = new Random(42);
        for (var i = 0; i < 20; i++)
        {
            var buf = new byte[rng.Next(0, 512)];
            rng.NextBytes(buf);
            ParseOrKnownFailure(parser, buf, "fuzz.qrp");
        }

        var full = FixturePackage.RequireBytes("fixture-a");
        foreach (var len in new[] { 0, 1, 10, 100, Math.Min(1000, full.Length / 2) })
        {
            ParseOrKnownFailure(parser, full.AsSpan(0, len).ToArray(), "trunc.qrp");
        }
    }

    private static void ParseOrKnownFailure(InterPdvQrpParser parser, byte[] bytes, string name)
    {
        try
        {
            var parsed = parser.Parse(new ParserInput(new MemoryStream(bytes), bytes.Length, name, "QRP"));
            Assert.NotNull(parsed);
        }
        catch (Exception ex) when (ex is IOException or ArgumentException or InvalidDataException)
        {
        }
    }

    private static string FindSourceRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            var candidate = Path.Combine(dir.FullName, "src", "Calciolari.DataHub");
            if (File.Exists(Path.Combine(candidate, "Calciolari.DataHub.csproj")))
            {
                return candidate;
            }

            dir = dir.Parent;
        }

        throw new InvalidOperationException("source root not found");
    }
}
