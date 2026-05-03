package com.pledgedecoding.election;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "candidates",
		uniqueConstraints = @UniqueConstraint(name = "uk_candidates_external", columnNames = {"election_id", "external_id"})
)
public class Candidate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "election_id", nullable = false)
	private Election election;

	@Column(name = "external_id", nullable = false, length = 40)
	private String externalId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "party_name", length = 100)
	private String partyName;

	@Column(name = "sd_name", length = 100)
	private String sdName;

	@Column(name = "sgg_name", length = 100)
	private String sggName;

	@Column(name = "wiw_name", length = 100)
	private String wiwName;

	@Column(length = 50)
	private String status;

	protected Candidate() {
	}

	public Candidate(Election election, String externalId, String name, String partyName, String sdName, String sggName, String wiwName, String status) {
		this.election = election;
		this.externalId = externalId;
		this.name = name;
		this.partyName = partyName;
		this.sdName = sdName;
		this.sggName = sggName;
		this.wiwName = wiwName;
		this.status = status;
	}

	public void updateDetails(String name, String partyName, String sdName, String sggName, String wiwName, String status) {
		this.name = name;
		this.partyName = partyName;
		this.sdName = sdName;
		this.sggName = sggName;
		this.wiwName = wiwName;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public Election getElection() {
		return election;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getName() {
		return name;
	}

	public String getPartyName() {
		return partyName;
	}

	public String getSdName() {
		return sdName;
	}

	public String getSggName() {
		return sggName;
	}

	public String getWiwName() {
		return wiwName;
	}

	public String getStatus() {
		return status;
	}
}
