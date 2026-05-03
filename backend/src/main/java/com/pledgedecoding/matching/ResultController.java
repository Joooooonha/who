package com.pledgedecoding.matching;

import static com.pledgedecoding.matching.ResultDtos.ResultRequest;
import static com.pledgedecoding.matching.ResultDtos.ResultResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/results")
public class ResultController {
	private final MatchingService matchingService;

	public ResultController(MatchingService matchingService) {
		this.matchingService = matchingService;
	}

	@PostMapping
	public ResultResponse calculate(@Valid @RequestBody ResultRequest request) {
		return matchingService.calculate(request);
	}
}

