package ai.architech.backend.core.verification;

import java.util.List;

public record SecretScanResult(List<SecretFinding> findings) {

	/** Any single finding blocks Runner Verification PASS / Candidate acceptance - AIW-158's own AC. */
	public boolean blocked() {
		return !findings.isEmpty();
	}
}
