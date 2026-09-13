package ai.architech.backend.core.sandbox;

public record ProcessResult(int exitCode, String stdout, String stderr) {

	public boolean succeeded() {
		return exitCode == 0;
	}
}
