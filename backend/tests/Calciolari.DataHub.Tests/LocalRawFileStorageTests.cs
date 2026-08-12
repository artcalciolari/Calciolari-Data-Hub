using Calciolari.DataHub.Imports.Infrastructure.Storage;

namespace Calciolari.DataHub.Tests;

public sealed class LocalRawFileStorageTests
{
    [Fact]
    public void PutIfAbsent_is_idempotent_and_verifies()
    {
        var root = Directory.CreateTempSubdirectory("raw").FullName;
        var storage = new LocalRawFileStorage(root);
        var bytes = "hello-qrp"u8.ToArray();
        var sha = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(bytes)).ToLowerInvariant();
        var descriptor = RawFileDescriptor.Create(sha, bytes.Length, "QRP");

        var first = storage.PutIfAbsent(new MemoryStream(bytes), descriptor);
        Assert.True(first.Created);
        Assert.True(storage.Exists(first.StorageKey));
        var second = storage.PutIfAbsent(new MemoryStream(bytes), descriptor);
        Assert.False(second.Created);

        using var opened = storage.OpenVerified(first.StorageKey, sha, bytes.Length);
        Assert.True(opened.Length >= 0);
        opened.Dispose();

        Assert.Throws<RawStorageIntegrityException>(() =>
            storage.OpenVerified(first.StorageKey, sha, 1));
        Assert.Throws<RawStorageIntegrityException>(() =>
            storage.OpenVerified(first.StorageKey, new string('0', 64), bytes.Length));
        Assert.Throws<RawStorageIntegrityException>(() =>
            storage.OpenVerified("aa/bb/" + new string('c', 64), sha, 1));
        Assert.Throws<UnauthorizedAccessException>(() => storage.Exists("../escape"));
        Assert.Throws<ArgumentException>(() => LocalRawFileStorage.StorageKeyFor("abc"));
        Assert.Equal(sha[..2] + "/" + sha[2..4] + "/" + sha, LocalRawFileStorage.StorageKeyFor(sha.ToUpperInvariant()));
        Assert.Equal(Path.GetFullPath(root), storage.Root);

        var mismatch = RawFileDescriptor.Create(new string('b', 64), bytes.Length, "QRP");
        Assert.Throws<RawStorageIntegrityException>(() => storage.PutIfAbsent(new MemoryStream(bytes), mismatch));

        var freshRoot = Directory.CreateTempSubdirectory("raw-size").FullName;
        var fresh = new LocalRawFileStorage(freshRoot);
        var sizeMismatchNew = RawFileDescriptor.Create(sha, bytes.Length + 1, "QRP");
        Assert.Throws<RawStorageIntegrityException>(() => fresh.PutIfAbsent(new MemoryStream(bytes), sizeMismatchNew));

        var sizeMismatch = RawFileDescriptor.Create(sha, bytes.Length + 1, "QRP");
        Assert.Throws<RawStorageIntegrityException>(() => storage.PutIfAbsent(new MemoryStream(bytes), sizeMismatch));

        File.WriteAllBytes(Path.Combine(root, first.StorageKey), "HELLO-QRP"u8.ToArray());
        Assert.Throws<RawStorageIntegrityException>(() => storage.PutIfAbsent(new MemoryStream(bytes), descriptor));
        File.WriteAllBytes(Path.Combine(root, first.StorageKey), bytes);

        using (var exclusive = new FileStream(Path.Combine(root, first.StorageKey), FileMode.Open, FileAccess.ReadWrite, FileShare.None))
        {
            Assert.Throws<IOException>(() => storage.OpenVerified(first.StorageKey, sha, bytes.Length));
            Assert.Throws<IOException>(() => storage.PutIfAbsent(new MemoryStream(bytes), descriptor));
        }

        Assert.False(storage.Exists("."));
        var trailing = new LocalRawFileStorage(root + Path.DirectorySeparatorChar);
        Assert.True(trailing.Exists(first.StorageKey));
    }

    [Fact]
    public void PutIfAbsent_adopts_target_when_move_loses_race()
    {
        var root = Directory.CreateTempSubdirectory("raw-race").FullName;
        var storage = new LocalRawFileStorage(root);
        var bytes = "race-qrp"u8.ToArray();
        var sha = Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(bytes)).ToLowerInvariant();
        var descriptor = RawFileDescriptor.Create(sha, bytes.Length, "QRP");
        var target = Path.Combine(root, LocalRawFileStorage.StorageKeyFor(sha));
        var planted = storage.PutIfAbsent(new PlantingStream(bytes, target, asDirectory: false), descriptor);
        Assert.False(planted.Created);

        var dirRoot = Directory.CreateTempSubdirectory("raw-dir").FullName;
        var dirStorage = new LocalRawFileStorage(dirRoot);
        var dirTarget = Path.Combine(dirRoot, LocalRawFileStorage.StorageKeyFor(sha));
        Assert.Throws<IOException>(() =>
            dirStorage.PutIfAbsent(new PlantingStream(bytes, dirTarget, asDirectory: true), descriptor));
    }

    private sealed class PlantingStream : MemoryStream
    {
        private readonly string _target;
        private readonly bool _asDirectory;
        private readonly byte[] _payload;

        public PlantingStream(byte[] payload, string target, bool asDirectory) : base(payload)
        {
            _payload = payload;
            _target = target;
            _asDirectory = asDirectory;
        }

        public override int Read(byte[] buffer, int offset, int count)
        {
            var read = base.Read(buffer, offset, count);
            if (Position >= Length)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(_target)!);
                if (_asDirectory)
                {
                    Directory.CreateDirectory(_target);
                }
                else if (!File.Exists(_target))
                {
                    File.WriteAllBytes(_target, _payload);
                }
            }

            return read;
        }
    }

    [Fact]
    public void Integrity_exception_constructors()
    {
        var inner = new IOException("x");
        var ex = new RawStorageIntegrityException("m", inner);
        Assert.Equal("m", ex.Message);
        Assert.Same(inner, ex.InnerException);
    }
}
