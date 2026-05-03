package com.pledgedecoding.matching;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class ResultDtos {
	private ResultDtos() {
	}

	public record ResultRequest(
			@NotEmpty List<@Valid PledgeResponseRequest> responses
	) {
	}

	public record PledgeResponseRequest(
			@NotNull Long pledgeId,
			@NotNull ResponseChoice choice
	) {
	}

	public record ResultResponse(
			List<CandidateMatchResponse> rankings,
			List<PledgeEvidenceResponse> evidence
	) {
	}

	public record CandidateMatchResponse(
			Long candidateId,
			String candidateName,
			String partyName,
			String region,
			int rank,
			int evaluatedPledgeCount,
			int totalScore,
			double averageScore,
			double matchPercentage
	) {
	}

	public record PledgeEvidenceResponse(
			Long pledgeId,
			String choice,
			int score,
			String candidateName,
			String partyName,
			String region,
			String category,
			String title,
			String summary,
			String officialText
	) {
	}
}

