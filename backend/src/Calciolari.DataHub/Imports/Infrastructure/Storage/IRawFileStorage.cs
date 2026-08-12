namespace Calciolari.DataHub.Imports.Infrastructure.Storage;

/// <summary>
/// Immutable raw artifact store. putIfAbsent must never clobber existing
/// bytes; hash/size divergence is an integrity failure.
/// </summary>
public interface IRawFileStorage
{
    StoredRawFile PutIfAbsent(Stream bytes, RawFileDescriptor descriptor);

    Stream OpenVerified(string storageKey, string expectedSha256, long expectedSize);

    bool Exists(string storageKey);
}
