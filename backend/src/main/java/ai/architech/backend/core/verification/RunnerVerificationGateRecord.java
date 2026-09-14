package ai.architech.backend.core.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One mandatory gate's persisted outcome within a {@link RunnerVerificationRun} (AIW-155).
 * {@code gateOrder} preserves the canonical 1-14 sequence ({@link
 * AuthoritativeRunnerVerifier#MANDATORY_GATE_NAMES}) independent of {@code gateName} string
 * sorting, so the persisted evidence can always be displayed/audited in the same fixed order
 * verification itself runs in. {@code detail} is the same bounded diagnostic {@link
 * GateResult#detail()} already carries - {@code null} for {@code PASS} and {@code SKIPPED}.
 */
@Entity
@Table(name = "runner_verification_gate")
public class RunnerVerificationGateRecord {

	@Id
	private UUID id;

	@Column(name = "runner_verification_run_id", nullable = false, updatable = false)
	private UUID runnerVerificationRunId;

	@Column(name = "gate_name", nullable = false, updatable = false)
	private String gateName;

	@Column(name = "gate_order", nullable = false, updatable = false)
	private int gateOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, updatable = false)
	private GateStatus status;

	@Column(name = "detail", updatable = false)
	private String detail;

	protected RunnerVerificationGateRecord() {
		// required by JPA
	}

	public RunnerVerificationGateRecord(UUID runnerVerificationRunId, String gateName, int gateOrder, GateStatus status, String detail) {
		this.id = UUID.randomUUID();
		this.runnerVerificationRunId = runnerVerificationRunId;
		this.gateName = gateName;
		this.gateOrder = gateOrder;
		this.status = status;
		this.detail = detail;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunnerVerificationRunId() {
		return runnerVerificationRunId;
	}

	public String getGateName() {
		return gateName;
	}

	public int getGateOrder() {
		return gateOrder;
	}

	public GateStatus getStatus() {
		return status;
	}

	public String getDetail() {
		return detail;
	}
}
