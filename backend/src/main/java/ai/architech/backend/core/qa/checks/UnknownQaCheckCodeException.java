package ai.architech.backend.core.qa.checks;

/** {@code registries/qa-checks.yaml}'s own "unknown check codes are rejected" (AIW-172). */
public class UnknownQaCheckCodeException extends RuntimeException {

	public UnknownQaCheckCodeException(String checkCode) {
		super("Unknown QA check code: '" + checkCode + "'");
	}
}
