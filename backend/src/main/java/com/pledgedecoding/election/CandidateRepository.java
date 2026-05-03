package com.pledgedecoding.election;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
	long countByElectionId(Long electionId);
}

