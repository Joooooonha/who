package com.pledgedecoding.matching;

public record CandidateScore(
		Long candidateId,
		String candidateName,
		String partyName,
		String region,
		int evaluatedPledgeCount,
		int totalScore,
		double averageScore,
		double matchPercentage
) {
}

