package ai.architech.backend.core.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** No Spring context - exercises {@link RequestLoggingFilter} directly against mock servlet objects. */
@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTests {

	private final RequestLoggingFilter filter = new RequestLoggingFilter();
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@Mock
	private FilterChain filterChain;

	@BeforeEach
	void attachAppender() {
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void detachAppenderAndClearMdc() {
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		logger.detachAppender(appender);
		MDC.clear();
	}

	@Test
	void logsOneStructuredLineWithMethodPathStatusAndDuration() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/123");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(404);

		filter.doFilter(request, response, filterChain);

		assertThat(appender.list).hasSize(1);
		ILoggingEvent event = appender.list.get(0);
		assertThat(event.getMDCPropertyMap()).containsEntry("httpMethod", "GET");
		assertThat(event.getMDCPropertyMap()).containsEntry("httpPath", "/api/projects/123");
		assertThat(event.getMDCPropertyMap()).containsEntry("httpStatus", "404");
		assertThat(event.getMDCPropertyMap()).containsKey("durationMs");
	}

	@Test
	void neverLogsHeadersOrRequestBody() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
		request.addHeader("Authorization", "Bearer super-secret-token");
		request.setContent("{\"apiKey\":\"sk-should-never-appear\"}".getBytes());
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(201);

		filter.doFilter(request, response, filterChain);

		ILoggingEvent event = appender.list.get(0);
		assertThat(event.getFormattedMessage()).doesNotContain("super-secret-token", "sk-should-never-appear", "Bearer");
		assertThat(event.getMDCPropertyMap()).doesNotContainKey("authorization");
	}

	@Test
	void passesTheRequestAndResponseThroughToTheFilterChainUnchanged() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
	}

	@Test
	void clearsItsOwnMdcEntriesAfterLogging() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertThat(MDC.get("httpMethod")).isNull();
		assertThat(MDC.get("httpPath")).isNull();
		assertThat(MDC.get("httpStatus")).isNull();
		assertThat(MDC.get("durationMs")).isNull();
	}
}
