package com.pledgedecoding.importer.nec;

public record NecCandidate(
		String sgId,
		String sgTypecode,
		String huboid,
		String sdName,
		String sggName,
		String wiwName,
		String partyName,
		String name,
		String status
) {
}
