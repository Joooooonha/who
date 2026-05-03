package com.pledgedecoding.election;

import static com.pledgedecoding.election.ElectionDtos.ElectionSummaryResponse;
import static com.pledgedecoding.election.ElectionDtos.PledgePlayResponse;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {
	private final ElectionService electionService;

	public ElectionController(ElectionService electionService) {
		this.electionService = electionService;
	}

	@GetMapping
	public List<ElectionSummaryResponse> listElections() {
		return electionService.listElections();
	}

	@GetMapping("/{electionId}/pledges")
	public List<PledgePlayResponse> listPlayablePledges(@PathVariable Long electionId) {
		return electionService.listPlayablePledges(electionId);
	}
}

