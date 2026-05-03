package com.pledgedecoding.election;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectionRepository extends JpaRepository<Election, Long> {
	List<Election> findAllByOrderByVotedateDescSgTypecodeAsc();

	Optional<Election> findBySgIdAndSgTypecode(String sgId, String sgTypecode);
}

