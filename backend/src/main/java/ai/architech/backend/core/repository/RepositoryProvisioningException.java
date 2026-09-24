package ai.architech.backend.core.repository;

public class RepositoryProvisioningException extends RuntimeException {

	public RepositoryProvisioningException(String message) {
		super(message);
	}

	public RepositoryProvisioningException(String message, Throwable cause) {
		super(message, cause);
	}
}
