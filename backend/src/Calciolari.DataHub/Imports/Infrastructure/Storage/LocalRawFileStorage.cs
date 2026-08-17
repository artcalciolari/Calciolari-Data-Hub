using System.Collections.Concurrent;
using System.Security.Cryptography;

namespace Calciolari.DataHub.Imports.Infrastructure.Storage;

/// <summary>
/// Filesystem raw store. Keys look like ab/cd/sha256; never derived from filenames.
/// PutIfAbsent is idempotent and never overwrites divergent bytes.
/// </summary>
public sealed class LocalRawFileStorage : IRawFileStorage
{
    private readonly string _root;
    private static readonly ConcurrentDictionary<string, object> Locks = new(StringComparer.Ordinal);

    public LocalRawFileStorage(string root)
    {
        ArgumentNullException.ThrowIfNull(root);
        _root = Path.GetFullPath(root);
    }

    public string Root => _root;

    public StoredRawFile PutIfAbsent(Stream bytes, RawFileDescriptor descriptor)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        ArgumentNullException.ThrowIfNull(descriptor);
        EnsureRoot();

        var sha = descriptor.Sha256.ToLowerInvariant();
        var storageKey = StorageKeyFor(sha);
        var target = ResolveKey(storageKey);

        lock (LockFor(sha))
        {
            if (File.Exists(target))
            {
                VerifyExisting(target, descriptor);
                return new StoredRawFile(storageKey, sha, descriptor.ByteSize, false);
            }

            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                var temp = Path.Combine(Path.GetDirectoryName(target)!, "." + sha + "." + Guid.NewGuid() + ".tmp");
                long written = 0;
                using (var digest = IncrementalHash.CreateHash(HashAlgorithmName.SHA256))
                using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                {
                    var buffer = new byte[8192];
                    int read;
                    while ((read = bytes.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        output.Write(buffer, 0, read);
                        digest.AppendData(buffer.AsSpan(0, read));
                        written += read;
                    }

                    var actualSha = Convert.ToHexString(digest.GetHashAndReset()).ToLowerInvariant();
                    if (!sha.Equals(actualSha, StringComparison.OrdinalIgnoreCase))
                    {
                        File.Delete(temp);
                        throw new RawStorageIntegrityException(
                            "stream SHA-256 mismatch: expected " + sha + " got " + actualSha);
                    }
                }

                if (written != descriptor.ByteSize)
                {
                    File.Delete(temp);
                    throw new RawStorageIntegrityException(
                        "stream size mismatch: expected " + descriptor.ByteSize + " got " + written);
                }

                try
                {
                    File.Move(temp, target);
                }
                catch (IOException)
                {
                    if (File.Exists(temp))
                    {
                        File.Delete(temp);
                    }

                    if (File.Exists(target))
                    {
                        VerifyExisting(target, descriptor);
                        return new StoredRawFile(storageKey, sha, descriptor.ByteSize, false);
                    }

                    throw;
                }

                return new StoredRawFile(storageKey, sha, descriptor.ByteSize, true);
            }
            catch (IOException ex)
            {
                throw new IOException("failed to store raw artifact " + sha, ex);
            }
        }
    }

    public Stream OpenVerified(string storageKey, string expectedSha256, long expectedSize)
    {
        var path = ResolveKey(storageKey);
        if (!File.Exists(path))
        {
            throw new RawStorageIntegrityException("missing raw artifact: " + storageKey);
        }

        try
        {
            var size = new FileInfo(path).Length;
            if (size != expectedSize)
            {
                throw new RawStorageIntegrityException(
                    "size mismatch for " + storageKey + ": expected " + expectedSize + " got " + size);
            }

            var actual = Sha256File(path);
            if (!expectedSha256.Equals(actual, StringComparison.OrdinalIgnoreCase))
            {
                throw new RawStorageIntegrityException(
                    "hash mismatch for " + storageKey + ": expected " + expectedSha256 + " got " + actual);
            }

            return File.OpenRead(path);
        }
        catch (IOException ex)
        {
            throw new IOException("failed to open raw artifact " + storageKey, ex);
        }
    }

    public bool Exists(string storageKey) => File.Exists(ResolveKey(storageKey));

    public int WipeAll()
    {
        if (!Directory.Exists(_root))
        {
            return 0;
        }

        var deleted = 0;
        foreach (var entry in Directory.EnumerateFileSystemEntries(_root))
        {
            if (Directory.Exists(entry))
            {
                deleted += Directory.GetFiles(entry, "*", SearchOption.AllDirectories).Length;
                Directory.Delete(entry, true);
            }
            else
            {
                File.Delete(entry);
                deleted++;
            }
        }

        return deleted;
    }

    public static string StorageKeyFor(string sha256)
    {
        var sha = sha256.ToLowerInvariant();
        if (sha.Length != 64)
        {
            throw new ArgumentException("sha256 must be 64 hex chars", nameof(sha256));
        }

        return sha[..2] + "/" + sha[2..4] + "/" + sha;
    }

    private static object LockFor(string sha) => Locks.GetOrAdd(sha, static _ => new object());

    private static void VerifyExisting(string target, RawFileDescriptor descriptor)
    {
        try
        {
            var size = new FileInfo(target).Length;
            if (size != descriptor.ByteSize)
            {
                throw new RawStorageIntegrityException(
                    "existing artifact size mismatch for " + descriptor.Sha256
                    + ": expected " + descriptor.ByteSize + " got " + size);
            }

            var actual = Sha256File(target);
            if (!descriptor.Sha256.Equals(actual, StringComparison.OrdinalIgnoreCase))
            {
                throw new RawStorageIntegrityException(
                    "existing artifact hash mismatch for key of " + descriptor.Sha256
                    + ": got " + actual);
            }
        }
        catch (IOException ex)
        {
            throw new IOException(ex.Message, ex);
        }
    }

    private string ResolveKey(string storageKey)
    {
        var resolved = Path.GetFullPath(Path.Combine(_root, storageKey));
        var rootWithSep = _root.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
                          + Path.DirectorySeparatorChar;
        if (!resolved.Equals(_root, StringComparison.Ordinal) &&
            !resolved.StartsWith(rootWithSep, StringComparison.Ordinal))
        {
            throw new UnauthorizedAccessException("storage key escapes root: " + storageKey);
        }

        return resolved;
    }

    private void EnsureRoot() => Directory.CreateDirectory(_root);

    private static string Sha256File(string path)
    {
        using var stream = File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
    }
}
