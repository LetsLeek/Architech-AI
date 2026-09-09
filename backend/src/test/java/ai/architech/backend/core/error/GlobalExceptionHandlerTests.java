package ai.architech.backend.core.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/** Plain unit tests, no Spring context - {@link GlobalExceptionHandler}'s methods are called directly. */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Mock
	private HttpServletRequest request;

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void mapsAnApplicationExceptionToItsErrorCodesHttpStatusAndMessage() {
		when(request.getRequestURI()).thenReturn("/api/projects/123");
		ApplicationException exception = new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "No project with id 123");

		ResponseEntity<ErrorResponse> response = handler.handleApplicationException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().errorCode()).isEqualTo("PROJECT_NOT_FOUND");
		assertThat(response.getBody().message()).isEqualTo("No project with id 123");
		assertThat(response.getBody().path()).isEqualTo("/api/projects/123");
	}

	@Test
	void includesTheCurrentCorrelationIdFromMdc() {
		when(request.getRequestURI()).thenReturn("/api/projects/123");
		MDC.put(RequestCorrelationFilter.MDC_KEY, "corr-42");

		ResponseEntity<ErrorResponse> response = handler.handleApplicationException(
				new ApplicationException(ErrorCode.PROJECT_NOT_FOUND, "not found"), request);

		assertThat(response.getBody().correlationId()).isEqualTo("corr-42");
	}

	@Test
	void mapsAResponseStatusExceptionToTheSameErrorResponseShape() {
		when(request.getRequestURI()).thenReturn("/api/projects");
		ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad input");

		ResponseEntity<ErrorResponse> response = handler.handleResponseStatusException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().errorCode()).isEqualTo("HTTP_400");
		assertThat(response.getBody().message()).isEqualTo("Bad input");
	}

	@Test
	void neverExposesAnUnexpectedExceptionsOwnMessageToTheClient() {
		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/api/projects");
		Exception exception = new NullPointerException("Cannot invoke \"Object.equals(Object)\" because \"o\" is null");

		ResponseEntity<ErrorResponse> response = handler.handleUnexpected(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred.");
		assertThat(response.getBody().message()).doesNotContain("Object.equals");
	}
}
