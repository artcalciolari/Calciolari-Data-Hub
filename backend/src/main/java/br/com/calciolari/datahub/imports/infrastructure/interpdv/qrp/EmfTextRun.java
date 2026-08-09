package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

/**
 * Text run extracted from {@code EMR_EXTTEXTOUTW} (type 84), as in the PoC
 * {@code parseEmfTexts}.
 */
public record EmfTextRun(int pageIndex, int x, int y, String text) {
}
