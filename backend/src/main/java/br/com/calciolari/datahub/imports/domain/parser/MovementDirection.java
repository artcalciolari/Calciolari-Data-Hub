package br.com.calciolari.datahub.imports.domain.parser;

/**
 * Movement direction taken only from positively identified source content.
 * Unknown stays UNKNOWN — never defaulted to OUT for revenue.
 */
public enum MovementDirection {
	OUT,
	IN,
	RETURN,
	UNKNOWN
}
