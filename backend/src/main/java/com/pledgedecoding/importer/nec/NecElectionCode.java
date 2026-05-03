package com.pledgedecoding.importer.nec;

import java.time.LocalDate;

public record NecElectionCode(
		String sgId,
		String sgTypecode,
		String sgName,
		LocalDate sgVotedate
) {
}
