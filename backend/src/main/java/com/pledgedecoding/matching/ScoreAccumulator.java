package com.pledgedecoding.matching;

final class ScoreAccumulator {
	private int evaluatedPledgeCount;
	private int totalScore;

	void add(int score) {
		this.evaluatedPledgeCount++;
		this.totalScore += score;
	}

	int evaluatedPledgeCount() {
		return evaluatedPledgeCount;
	}

	int totalScore() {
		return totalScore;
	}

	double averageScore() {
		if (evaluatedPledgeCount == 0) {
			return 0;
		}
		return (double) totalScore / evaluatedPledgeCount;
	}

	double matchPercentage() {
		double normalized = ((averageScore() - 1.0) / 2.0) * 100.0;
		return Math.max(0, Math.min(100, normalized));
	}
}

