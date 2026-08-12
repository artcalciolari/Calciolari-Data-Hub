namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Deep module seam for source-specific importers. QRP/EMF types must not leak
/// past implementations of this interface.
/// </summary>
public interface IImportParser
{
    bool Supports(ParserInput input);

    ParsedImport Parse(ParserInput input);
}
