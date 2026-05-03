package com.pledgedecoding.election;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PledgeRepository extends JpaRepository<Pledge, Long> {
	long countByCandidateElectionId(Long electionId);

	Optional<Pledge> findByCandidateIdAndExternalOrder(Long candidateId, int externalOrder);

	@EntityGraph(attributePaths = {"candidate", "candidate.election"})
	@Query("""
			select p
			from Pledge p
			where p.candidate.election.id = :electionId
			order by p.category asc, p.title asc, p.id asc
			""")
	List<Pledge> findPlayablePledges(@Param("electionId") Long electionId);

	@EntityGraph(attributePaths = {"candidate", "candidate.election"})
	List<Pledge> findByIdIn(Collection<Long> ids);
}
