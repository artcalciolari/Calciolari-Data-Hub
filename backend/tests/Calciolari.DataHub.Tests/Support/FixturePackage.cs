using System.Security.Cryptography;
using System.Text.Json;

namespace Calciolari.DataHub.Tests.Support;

public static class FixturePackage
{
    public static JsonElement Manifest()
    {
        var path = Resolve("manifest.json");
        return JsonDocument.Parse(File.ReadAllText(path)).RootElement;
    }

    public static JsonElement RequireFixture(string id)
    {
        foreach (var fixture in Manifest().GetProperty("fixtures").EnumerateArray())
        {
            if (fixture.GetProperty("id").GetString() == id)
            {
                return fixture;
            }
        }

        throw new ArgumentException("Unknown fixture id: " + id);
    }

    public static byte[] RequireBytes(string fixtureId)
    {
        var fixture = RequireFixture(fixtureId);
        var status = fixture.GetProperty("status").GetString();
        var relativePath = fixture.GetProperty("relativePath").GetString();
        if (status != "PRESENT" || relativePath is null)
        {
            throw new InvalidOperationException("Fixture '" + fixtureId + "' is not present.");
        }

        var external = ResolveExternal(relativePath);
        var bytes = File.ReadAllBytes(external ?? Resolve(relativePath));
        VerifyHash(bytes, fixture);
        return bytes;
    }

    public static bool IsPresent(string fixtureId)
    {
        var fixture = RequireFixture(fixtureId);
        if (fixture.GetProperty("status").GetString() != "PRESENT")
        {
            return false;
        }

        var relativePath = fixture.GetProperty("relativePath").GetString();
        if (relativePath is null)
        {
            return false;
        }

        if (ResolveExternal(relativePath) is not null)
        {
            return true;
        }

        return File.Exists(Resolve(relativePath));
    }

    private static string? ResolveExternal(string relativePath)
    {
        var root = Environment.GetEnvironmentVariable("DATAHUB_FIXTURES_DIR");
        if (string.IsNullOrWhiteSpace(root))
        {
            return null;
        }

        var candidate = Path.Combine(root, relativePath);
        if (File.Exists(candidate))
        {
            return candidate;
        }

        var flat = Path.Combine(root, Path.GetFileName(relativePath));
        return File.Exists(flat) ? flat : null;
    }

    private static string Resolve(string relativePath)
    {
        var baseDir = AppContext.BaseDirectory;
        var path = Path.Combine(baseDir, "fixtures", relativePath);
        if (File.Exists(path))
        {
            return path;
        }

        throw new InvalidOperationException("Fixture missing: " + relativePath);
    }

    private static void VerifyHash(byte[] bytes, JsonElement fixture)
    {
        var expectedSha = fixture.GetProperty("sha256").GetString();
        var expectedSize = fixture.GetProperty("byteSize").GetInt64();
        if (bytes.Length != expectedSize)
        {
            throw new InvalidOperationException("Fixture size mismatch");
        }

        var actual = Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
        if (!string.Equals(expectedSha, actual, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException("Fixture SHA-256 mismatch");
        }
    }
}
