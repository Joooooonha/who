package com.pledgedecoding.election;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
	long countByElectionId(Long electionId);

	Optional<Candidate> findByElectionIdAndExternalId(Long electionId, String externalId);
}
