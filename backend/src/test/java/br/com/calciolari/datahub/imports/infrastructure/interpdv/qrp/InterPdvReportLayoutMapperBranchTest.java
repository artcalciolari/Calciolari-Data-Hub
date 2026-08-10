package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;

class InterPdvReportLayoutMapperBranchTest {

	@Test
	void mapsSyntheticRowsForDirectionBranches() {
		List<EmfTextRun> items = new ArrayList<>();
		items.add(new EmfTextRun(0, 0, 0, "Produto: 99 - Widget"));
		items.add(new EmfTextRun(0, 100, 10, "Preço"));
		items.add(new EmfTextRun(0, 200, 10, "Saidas"));
		items.add(new EmfTextRun(0, 300, 10, "Entradas"));
		items.add(new EmfTextRun(0, 400, 10, "Data"));
		items.add(new EmfTextRun(0, 500, 10, "Hora"));
		items.add(new EmfTextRun(0, 600, 10, "Total Item"));
		items.add(new EmfTextRun(0, 0, 20, "Venda Numero: 1"));
		items.add(new EmfTextRun(0, 200, 20, "1,0"));
		items.add(new EmfTextRun(0, 400, 20, "01/01/2024"));
		items.add(new EmfTextRun(0, 500, 20, "10:00:00"));
		items.add(new EmfTextRun(0, 600, 20, "10,00"));
		items.add(new EmfTextRun(0, 100, 20, "10,00"));
		items.add(new EmfTextRun(0, 0, 30, "Venda Numero: 2"));
		items.add(new EmfTextRun(0, 300, 30, "2,0"));
		items.add(new EmfTextRun(0, 0, 40, "Venda Numero: 3"));
		items.add(new EmfTextRun(0, 200, 40, "1,0"));
		items.add(new EmfTextRun(0, 300, 40, "1,0"));
		items.add(new EmfTextRun(0, 0, 50, "Venda Numero: 4"));
		items.add(new EmfTextRun(0, 400, 50, "01/99/2024"));
		items.add(new EmfTextRun(0, 500, 50, "99:99:99"));
		items.add(new EmfTextRun(0, 0, 60, "Total de Vendas:"));
		items.add(new EmfTextRun(0, 50, 60, "3,0"));

		var mapped = new InterPdvReportLayoutMapper().map(items, 1);
		assertEquals("99", mapped.externalProductId());
		assertTrue(mapped.movements().size() >= 3);
		assertTrue(mapped.movements().stream().anyMatch(m -> m.direction() == MovementDirection.OUT));
		assertTrue(mapped.movements().stream().anyMatch(m -> m.direction() == MovementDirection.IN));
		assertTrue(mapped.issues().stream().anyMatch(i -> "INVALID_DATETIME".equals(i.code())));
	}

	@Test
	void noSaleRowsAddsError() {
		var mapped = new InterPdvReportLayoutMapper().map(
				List.of(new EmfTextRun(0, 0, 0, "Produto: 1 - X")), 1);
		assertTrue(mapped.issues().stream()
				.anyMatch(i -> "NO_SALE_ROWS".equals(i.code()) && i.severity() == IssueSeverity.ERROR));
		assertTrue(mapped.movements().isEmpty());
	}
}
