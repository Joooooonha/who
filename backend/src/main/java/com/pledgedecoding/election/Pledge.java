package com.pledgedecoding.election;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "pledges",
		uniqueConstraints = @UniqueConstraint(name = "uk_pledges_candidate_order", columnNames = {"candidate_id", "external_order"})
)
public class Pledge {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "candidate_id", nullable = false)
	private Candidate candidate;

	@Column(name = "external_order", nullable = false)
	private int externalOrder;

	@Column(length = 100)
	private String category;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(name = "raw_content", nullable = false, columnDefinition = "text")
	private String rawContent;

	@Column(columnDefinition = "text")
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(name = "summary_status", nullable = false, length = 30)
	private SummaryStatus summaryStatus = SummaryStatus.PENDING;

	@Column(name = "summary_model", length = 100)
	private String summaryModel;

	@Column(name = "summary_generated_at")
	private Instant summaryGeneratedAt;

	@Column(name = "source_hash", length = 128)
	private String sourceHash;

	protected Pledge() {
	}

	public Pledge(Candidate candidate, int externalOrder, String category, String title, String rawContent) {
		this.candidate = candidate;
		this.externalOrder = externalOrder;
		this.category = category;
		this.title = title;
		this.rawContent = rawContent;
	}

	public Long getId() {
		return id;
	}

	public Candidate getCandidate() {
		return candidate;
	}

	public int getExternalOrder() {
		return externalOrder;
	}

	public String getCategory() {
		return category;
	}

	public String getTitle() {
		return title;
	}

	public String getRawContent() {
		return rawContent;
	}

	public String getSummary() {
		return summary;
	}

	public SummaryStatus getSummaryStatus() {
		return summaryStatus;
	}

	public String getSummaryModel() {
		return summaryModel;
	}

	public Instant getSummaryGeneratedAt() {
		return summaryGeneratedAt;
	}

	public String getSourceHash() {
		return sourceHash;
	}
}

