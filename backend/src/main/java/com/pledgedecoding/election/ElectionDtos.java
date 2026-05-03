package com.pledgedecoding.election;

import java.time.LocalDate;

public final class ElectionDtos {
	private ElectionDtos() {
	}

	public record ElectionSummaryResponse(
			Long id,
			String sgId,
			String sgTypecode,
			String name,
			LocalDate votedate,
			long candidateCount,
			long pledgeCount
	) {
	}

	public record PledgePlayResponse(
			Long id,
			Long electionId,
			String category,
			String title,
			String officialText,
			String summary,
			SummaryStatus summaryStatus
	) {
	}
}

