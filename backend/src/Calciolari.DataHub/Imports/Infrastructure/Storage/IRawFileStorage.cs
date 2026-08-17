namespace Calciolari.DataHub.Imports.Infrastructure.Storage;

/// <summary>
/// Immutable raw artifact store. PutIfAbsent must never clobber existing
/// bytes; hash/size divergence is an integrity failure. WipeAll is the
/// debug-only exception used to rebuild an empty dataset.
/// </summary>
public interface IRawFileStorage
{
    StoredRawFile PutIfAbsent(Stream bytes, RawFileDescriptor descriptor);

    Stream OpenVerified(string storageKey, string expectedSha256, long expectedSize);

    bool Exists(string storageKey);

    int WipeAll();
}
