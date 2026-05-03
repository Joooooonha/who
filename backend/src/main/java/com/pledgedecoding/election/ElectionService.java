package com.pledgedecoding.election;

import static com.pledgedecoding.election.ElectionDtos.ElectionSummaryResponse;
import static com.pledgedecoding.election.ElectionDtos.PledgePlayResponse;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ElectionService {
	private final ElectionRepository electionRepository;
	private final CandidateRepository candidateRepository;
	private final PledgeRepository pledgeRepository;

	public ElectionService(ElectionRepository electionRepository, CandidateRepository candidateRepository, PledgeRepository pledgeRepository) {
		this.electionRepository = electionRepository;
		this.candidateRepository = candidateRepository;
		this.pledgeRepository = pledgeRepository;
	}

	public List<ElectionSummaryResponse> listElections() {
		return electionRepository.findAllByOrderByVotedateDescSgTypecodeAsc()
				.stream()
				.map(election -> new ElectionSummaryResponse(
						election.getId(),
						election.getSgId(),
						election.getSgTypecode(),
						election.getName(),
						election.getVotedate(),
						candidateRepository.countByElectionId(election.getId()),
						pledgeRepository.countByCandidateElectionId(election.getId())
				))
				.toList();
	}

	public List<PledgePlayResponse> listPlayablePledges(Long electionId) {
		if (!electionRepository.existsById(electionId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found");
		}

		return pledgeRepository.findPlayablePledges(electionId)
				.stream()
				.map(pledge -> new PledgePlayResponse(
						pledge.getId(),
						pledge.getCandidate().getElection().getId(),
						pledge.getCategory(),
						pledge.getTitle(),
						pledge.getRawContent(),
						pledge.getSummary(),
						pledge.getSummaryStatus()
				))
				.toList();
	}
}

