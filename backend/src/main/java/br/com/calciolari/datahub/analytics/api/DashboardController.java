package br.com.calciolari.datahub.analytics.api;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.calciolari.datahub.analytics.api.DashboardDtos.DashboardResponse;
import br.com.calciolari.datahub.analytics.application.DashboardQueryService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardQueryService dashboardQueryService;

	public DashboardController(DashboardQueryService dashboardQueryService) {
		this.dashboardQueryService = dashboardQueryService;
	}

	@GetMapping
	public DashboardResponse get(
			@RequestParam(required = false) UUID productId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
		return dashboardQueryService.summarize(productId, from, to);
	}
}
