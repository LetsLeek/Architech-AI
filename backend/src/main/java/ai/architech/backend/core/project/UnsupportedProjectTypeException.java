package ai.architech.backend.core.project;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnsupportedProjectTypeException extends RuntimeException {

	public UnsupportedProjectTypeException(String projectType) {
		super("Unsupported project type: " + projectType);
	}
}
