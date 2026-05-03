package com.pledgedecoding.matching;

import static com.pledgedecoding.matching.ResultDtos.CandidateMatchResponse;
import static com.pledgedecoding.matching.ResultDtos.PledgeEvidenceResponse;
import static com.pledgedecoding.matching.ResultDtos.PledgeResponseRequest;
import static com.pledgedecoding.matching.ResultDtos.ResultRequest;
import static com.pledgedecoding.matching.ResultDtos.ResultResponse;

import com.pledgedecoding.election.Candidate;
import com.pledgedecoding.election.Pledge;
import com.pledgedecoding.election.PledgeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class MatchingService {
	private final PledgeRepository pledgeRepository;

	public MatchingService(PledgeRepository pledgeRepository) {
		this.pledgeRepository = pledgeRepository;
	}

	public ResultResponse calculate(ResultRequest request) {
		Map<Long, PledgeResponseRequest> responseByPledgeId = request.responses()
				.stream()
				.collect(Collectors.toMap(
						PledgeResponseRequest::pledgeId,
						Function.identity(),
						(first, ignored) -> first,
						LinkedHashMap::new
				));

		List<Pledge> pledges = pledgeRepository.findByIdIn(responseByPledgeId.keySet());
		if (pledges.size() != responseByPledgeId.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Some pledge IDs do not exist");
		}

		Map<Long, ScoreAccumulator> scores = new LinkedHashMap<>();
		Map<Long, Candidate> candidates = new LinkedHashMap<>();
		for (Pledge pledge : pledges) {
			Candidate candidate = pledge.getCandidate();
			candidates.put(candidate.getId(), candidate);
			scores.computeIfAbsent(candidate.getId(), ignored -> new ScoreAccumulator())
					.add(responseByPledgeId.get(pledge.getId()).choice().score());
		}

		List<CandidateScore> candidateScores = scores.entrySet()
				.stream()
				.map(entry -> toCandidateScore(candidates.get(entry.getKey()), entry.getValue()))
				.sorted(Comparator
						.comparingDouble(CandidateScore::matchPercentage).reversed()
						.thenComparing(Comparator.comparingInt(CandidateScore::evaluatedPledgeCount).reversed())
						.thenComparing(CandidateScore::candidateName))
				.toList();

		List<CandidateMatchResponse> rankings = toRankings(candidateScores);
		List<PledgeEvidenceResponse> evidence = pledges.stream()
				.map(pledge -> toEvidence(pledge, responseByPledgeId.get(pledge.getId()).choice()))
				.toList();

		return new ResultResponse(rankings, evidence);
	}

	private static CandidateScore toCandidateScore(Candidate candidate, ScoreAccumulator accumulator) {
		return new CandidateScore(
				candidate.getId(),
				candidate.getName(),
				candidate.getPartyName(),
				region(candidate),
				accumulator.evaluatedPledgeCount(),
				accumulator.totalScore(),
				round(accumulator.averageScore()),
				round(accumulator.matchPercentage())
		);
	}

	private static List<CandidateMatchResponse> toRankings(List<CandidateScore> scores) {
		int[] rank = {0};
		return scores.stream()
				.map(score -> new CandidateMatchResponse(
						score.candidateId(),
						score.candidateName(),
						score.partyName(),
						score.region(),
						++rank[0],
						score.evaluatedPledgeCount(),
						score.totalScore(),
						score.averageScore(),
						score.matchPercentage()
				))
				.toList();
	}

	private static PledgeEvidenceResponse toEvidence(Pledge pledge, ResponseChoice choice) {
		Candidate candidate = pledge.getCandidate();
		return new PledgeEvidenceResponse(
				pledge.getId(),
				choice.name(),
				choice.score(),
				candidate.getName(),
				candidate.getPartyName(),
				region(candidate),
				pledge.getCategory(),
				pledge.getTitle(),
				pledge.getSummary(),
				pledge.getRawContent()
		);
	}

	private static String region(Candidate candidate) {
		if (candidate.getSggName() != null && !candidate.getSggName().isBlank()) {
			return candidate.getSggName();
		}
		if (candidate.getSdName() != null && !candidate.getSdName().isBlank()) {
			return candidate.getSdName();
		}
		return "";
	}

	private static double round(double value) {
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}
}

