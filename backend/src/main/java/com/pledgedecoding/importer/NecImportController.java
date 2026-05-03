package com.pledgedecoding.importer;

import static com.pledgedecoding.importer.NecImportDtos.NecImportResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/import")
public class NecImportController {
	private final NecImportService necImportService;

	public NecImportController(NecImportService necImportService) {
		this.necImportService = necImportService;
	}

	@PostMapping("/nec")
	public NecImportResponse importNecElection(
			@RequestParam @NotBlank String sgId,
			@RequestParam @NotBlank String sgTypecode,
			@RequestParam(defaultValue = "") String sdName,
			@RequestParam(defaultValue = "") String sggName,
			@RequestParam(defaultValue = "500") @PositiveOrZero int candidateLimit
	) {
		return necImportService.importElection(sgId, sgTypecode, sdName, sggName, candidateLimit);
	}
}
