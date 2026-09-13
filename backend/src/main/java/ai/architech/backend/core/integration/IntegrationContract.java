package ai.architech.backend.core.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One project-scoped, already-Developer-safe Integration Contract (AIW-144). There is
 * deliberately no field anywhere on this entity for a secret value, provider administration
 * credential or deployment credential - {@link #safeContractContent} is the entire persisted
 * content, and it is exactly (and only ever) a document already shaped like
 * {@code urn:aiw:schema:developer-safe-integration-contract-view:v1} (interface/operation
 * contracts, allowed runtime targets, symbolic - never literal - secret/config names, per
 * {@code docs/INTEGRATION-BOUNDARY.md} in the frozen spec package). A broader, secret-bearing
 * "raw" Integration Contract representation (for whatever real admin/provisioning process
 * creates these rows) does not exist anywhere in this codebase and is out of scope here: no
 * ticket in the Website Developer Agent V1 epic asks this platform to build provider/secret
 * administration, only to prove the Developer-facing projection can never carry one.
 *
 * <p>{@code contractRef} is unique per project, never globally - resolution is always scoped by
 * {@code (projectId, contractRef)} together (see {@link IntegrationContractResolver}), exactly
 * like {@code core.asset.ProjectAsset}, so a contract belonging to a different project can never
 * be resolved by ref alone. Rows are immutable once created (no setters) - "immutable version
 * binding" per AIW-144's own acceptance criteria; a new contract version is a new row, never a
 * mutation of {@link #version} on this one.
 */
@Entity
@Table(name = "integration_contract")
public class IntegrationContract {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(name = "contract_ref", nullable = false, updatable = false)
	private String contractRef;

	@Column(name = "version", nullable = false, updatable = false)
	private int version;

	@Column(name = "safe_contract_content", nullable = false, updatable = false)
	private String safeContractContent;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected IntegrationContract() {
		// required by JPA
	}

	public IntegrationContract(UUID projectId, String contractRef, int version, String safeContractContent) {
		this.id = UUID.randomUUID();
		this.projectId = projectId;
		this.contractRef = contractRef;
		this.version = version;
		this.safeContractContent = safeContractContent;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getContractRef() {
		return contractRef;
	}

	public int getVersion() {
		return version;
	}

	public String getSafeContractContent() {
		return safeContractContent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
