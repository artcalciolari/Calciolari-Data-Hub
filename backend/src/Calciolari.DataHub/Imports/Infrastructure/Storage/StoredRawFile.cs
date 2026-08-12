namespace Calciolari.DataHub.Imports.Infrastructure.Storage;

public sealed record StoredRawFile(string StorageKey, string Sha256, long ByteSize, bool Created);
