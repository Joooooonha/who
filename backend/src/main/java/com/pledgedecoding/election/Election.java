package com.pledgedecoding.election;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

@Entity
@Table(
		name = "elections",
		uniqueConstraints = @UniqueConstraint(name = "uk_elections_code", columnNames = {"sg_id", "sg_typecode"})
)
public class Election {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sg_id", nullable = false, length = 20)
	private String sgId;

	@Column(name = "sg_typecode", nullable = false, length = 10)
	private String sgTypecode;

	@Column(nullable = false, length = 100)
	private String name;

	private LocalDate votedate;

	protected Election() {
	}

	public Election(String sgId, String sgTypecode, String name, LocalDate votedate) {
		this.sgId = sgId;
		this.sgTypecode = sgTypecode;
		this.name = name;
		this.votedate = votedate;
	}

	public void updateDetails(String name, LocalDate votedate) {
		this.name = name;
		this.votedate = votedate;
	}

	public Long getId() {
		return id;
	}

	public String getSgId() {
		return sgId;
	}

	public String getSgTypecode() {
		return sgTypecode;
	}

	public String getName() {
		return name;
	}

	public LocalDate getVotedate() {
		return votedate;
	}
}
