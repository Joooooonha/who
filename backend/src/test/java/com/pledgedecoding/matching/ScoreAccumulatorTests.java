package com.pledgedecoding.matching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScoreAccumulatorTests {
	@Test
	void normalizesAverageScoreToPercentage() {
		ScoreAccumulator accumulator = new ScoreAccumulator();

		accumulator.add(ResponseChoice.POSITIVE.score());
		accumulator.add(ResponseChoice.NEUTRAL.score());
		accumulator.add(ResponseChoice.NEGATIVE.score());

		assertThat(accumulator.averageScore()).isEqualTo(2.0);
		assertThat(accumulator.matchPercentage()).isEqualTo(50.0);
	}

	@Test
	void maxScoreIsOneHundredPercent() {
		ScoreAccumulator accumulator = new ScoreAccumulator();

		accumulator.add(ResponseChoice.POSITIVE.score());
		accumulator.add(ResponseChoice.POSITIVE.score());

		assertThat(accumulator.averageScore()).isEqualTo(3.0);
		assertThat(accumulator.matchPercentage()).isEqualTo(100.0);
	}
}

