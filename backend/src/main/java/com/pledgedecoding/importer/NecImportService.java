package com.pledgedecoding.importer;

import static com.pledgedecoding.importer.NecImportDtos.NecImportResponse;

import com.pledgedecoding.election.Candidate;
import com.pledgedecoding.election.CandidateRepository;
import com.pledgedecoding.election.Election;
import com.pledgedecoding.election.ElectionRepository;
import com.pledgedecoding.election.Pledge;
import com.pledgedecoding.election.PledgeRepository;
import com.pledgedecoding.importer.nec.NecApiClient;
import com.pledgedecoding.importer.nec.NecCandidate;
import com.pledgedecoding.importer.nec.NecElectionCode;
import com.pledgedecoding.importer.nec.NecPledge;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NecImportService {
	private final NecApiClient necApiClient;
	private final ElectionRepository electionRepository;
	private final CandidateRepository candidateRepository;
	private final PledgeRepository pledgeRepository;

	public NecImportService(
			NecApiClient necApiClient,
			ElectionRepository electionRepository,
			CandidateRepository candidateRepository,
			PledgeRepository pledgeRepository
	) {
		this.necApiClient = necApiClient;
		this.electionRepository = electionRepository;
		this.candidateRepository = candidateRepository;
		this.pledgeRepository = pledgeRepository;
	}

	@Transactional
	public NecImportResponse importElection(String sgId, String sgTypecode, String sdName, String sggName, int candidateLimit) {
		NecElectionCode electionCode = necApiClient.fetchElectionCodes()
				.stream()
				.filter(code -> code.sgId().equals(sgId) && code.sgTypecode().equals(sgTypecode))
				.findFirst()
				.orElse(new NecElectionCode(sgId, sgTypecode, "NEC election " + sgId + "/" + sgTypecode, parseVotedate(sgId)));

		Election election = electionRepository.findBySgIdAndSgTypecode(sgId, sgTypecode)
				.orElseGet(() -> electionRepository.save(new Election(
						electionCode.sgId(),
						electionCode.sgTypecode(),
						electionCode.sgName(),
						electionCode.sgVotedate()
				)));
		election.updateDetails(electionCode.sgName(), electionCode.sgVotedate());

		List<NecCandidate> necCandidates = necApiClient.fetchCandidates(sgId, sgTypecode, sdName, sggName, candidateLimit);

		int candidatesSaved = 0;
		int candidatesWithoutPledges = 0;
		int pledgesFetched = 0;
		int pledgesSaved = 0;

		for (NecCandidate necCandidate : necCandidates) {
			Candidate candidate = candidateRepository.findByElectionIdAndExternalId(election.getId(), necCandidate.huboid())
					.orElseGet(() -> candidateRepository.save(new Candidate(
							election,
							necCandidate.huboid(),
							necCandidate.name(),
							necCandidate.partyName(),
							necCandidate.sdName(),
							necCandidate.sggName(),
							necCandidate.wiwName(),
							necCandidate.status()
					)));
			candidate.updateDetails(
					necCandidate.name(),
					necCandidate.partyName(),
					necCandidate.sdName(),
					necCandidate.sggName(),
					necCandidate.wiwName(),
					necCandidate.status()
			);
			candidatesSaved++;

			List<NecPledge> necPledges = necApiClient.fetchPledges(sgId, sgTypecode, necCandidate.huboid());
			if (necPledges.isEmpty()) {
				candidatesWithoutPledges++;
				continue;
			}
			pledgesFetched += necPledges.size();
			for (NecPledge necPledge : necPledges) {
				Pledge pledge = pledgeRepository.findByCandidateIdAndExternalOrder(candidate.getId(), necPledge.order())
						.orElseGet(() -> pledgeRepository.save(new Pledge(
								candidate,
								necPledge.order(),
								necPledge.category(),
								necPledge.title(),
								necPledge.rawContent()
						)));
				pledge.updateOfficialContent(necPledge.category(), necPledge.title(), necPledge.rawContent());
				pledgesSaved++;
			}
		}

		return new NecImportResponse(
				sgId,
				sgTypecode,
				election.getName(),
				necCandidates.size(),
				candidatesSaved,
				candidatesWithoutPledges,
				pledgesFetched,
				pledgesSaved
		);
	}

	private static LocalDate parseVotedate(String sgId) {
		if (sgId == null || sgId.length() != 8) {
			return null;
		}
		try {
			return LocalDate.of(
					Integer.parseInt(sgId.substring(0, 4)),
					Integer.parseInt(sgId.substring(4, 6)),
					Integer.parseInt(sgId.substring(6, 8))
			);
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
