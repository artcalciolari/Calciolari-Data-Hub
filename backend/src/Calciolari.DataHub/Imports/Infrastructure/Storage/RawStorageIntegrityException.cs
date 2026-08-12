namespace Calciolari.DataHub.Imports.Infrastructure.Storage;

/// <summary>
/// Raised when an existing raw artifact disagrees on hash or size (no-clobber integrity).
/// </summary>
public sealed class RawStorageIntegrityException : Exception
{
    public RawStorageIntegrityException(string message)
        : base(message)
    {
    }

    public RawStorageIntegrityException(string message, Exception inner)
        : base(message, inner)
    {
    }
}
