package br.com.calciolari.datahub.sales.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.sales.api.SaleDtos.SaleDetail;
import br.com.calciolari.datahub.sales.api.SaleDtos.SaleSummary;
import br.com.calciolari.datahub.sales.application.SaleQueryService;
import br.com.calciolari.datahub.shared.api.PageResponse;

class SaleControllerUnitTest {

	@Test
	void listAndGetDelegate() {
		SaleQueryService query = mock(SaleQueryService.class);
		SaleController controller = new SaleController(query);
		PageResponse<SaleSummary> page = PageResponse.of(List.of(), 0, 20, 0);
		when(query.list(isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(page);
		assertSame(page, controller.list(null, null, null, null, null));
		verify(query).list(isNull(), isNull(), isNull(), anyInt(), anyInt());

		UUID id = UUID.randomUUID();
		SaleDetail detail = mock(SaleDetail.class);
		when(query.get(id)).thenReturn(detail);
		assertSame(detail, controller.get(id));
	}
}
