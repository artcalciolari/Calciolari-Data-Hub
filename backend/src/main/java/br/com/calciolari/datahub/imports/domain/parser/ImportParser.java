package br.com.calciolari.datahub.imports.domain.parser;

/**
 * Deep module seam for source-specific importers. QRP/EMF types must not leak
 * past implementations of this interface.
 */
public interface ImportParser {

	boolean supports(ParserInput input);

	ParsedImport parse(ParserInput input);
}
