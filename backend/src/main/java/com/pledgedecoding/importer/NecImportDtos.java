package com.pledgedecoding.importer;

public final class NecImportDtos {
	private NecImportDtos() {
	}

	public record NecImportResponse(
			String sgId,
			String sgTypecode,
			String electionName,
			int candidatesFetched,
			int candidatesSaved,
			int candidatesWithoutPledges,
			int pledgesFetched,
			int pledgesSaved
	) {
	}
}
