package com.pledgedecoding.matching;

public enum ResponseChoice {
	POSITIVE(3),
	NEUTRAL(2),
	NEGATIVE(1);

	private final int score;

	ResponseChoice(int score) {
		this.score = score;
	}

	public int score() {
		return score;
	}
}

