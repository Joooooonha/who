package com.pledgedecoding.importer.nec;

public record NecPledge(
		String sgId,
		String sgTypecode,
		String cnddtId,
		String candidateName,
		String partyName,
		String sdName,
		String sggName,
		int order,
		String category,
		String title,
		String rawContent
) {
}
