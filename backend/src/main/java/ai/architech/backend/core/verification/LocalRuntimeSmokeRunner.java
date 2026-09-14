package ai.architech.backend.core.verification;

import ai.architech.backend.core.sandbox.ProjectExecutionCapability;
import ai.architech.backend.core.sandbox.Workspace;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Drives a real headless Chromium (Playwright for Java) against a Developer execution's own
 * locally-started dev server (AIW-143's gates 7-12): starts the runtime, waits for it to become
 * ready, loads the canonical route, follows every same-origin link discovered there (the
 * "primary navigation" set - this harness has no other way to know a generated site's actual
 * navigation structure without hard-coding assumptions about it), and checks both a wide and a
 * narrow viewport for horizontal overflow. Every network request and console/page error/failed
 * request observed along the way is collected raw; classifying which are policy violations vs.
 * genuine runtime problems is {@code AuthoritativeRunnerVerifier}'s job, using {@link
 * NetworkPolicyChecker} - this class only observes.
 *
 * <p>{@code startedSuccessfully() == false} is reserved for a genuine infrastructure failure (the
 * dev server never came up, or the browser itself failed to launch/navigate) - never for
 * anything about the generated site's own content, which always comes back as data on a
 * successful {@link LocalRuntimeSmokeOutcome} for the caller to judge.
 *
 * <p>Each {@link ObservedRequest#purpose()} is Playwright's own request {@code resourceType()}
 * (e.g. {@code "stylesheet"}, {@code "script"}, {@code "image"}, {@code "fetch"}) - a generic
 * browser observer has no way to know a request's actual business purpose (which Runtime
 * Profile capability or Integration Contract binding it serves), only what kind of resource it
 * technically is. An {@link AuthorizedExternalTarget} therefore authorizes a host for one of
 * these resource-type strings, not a semantic label - callers building an authorization list
 * from execution context need to express it in exactly this vocabulary.
 */
@Component
public class LocalRuntimeSmokeRunner {

	// AIW-138's scaffold has no custom server.port config, so `npm run dev` (ProjectExecutionTask
	// .LOCAL_RUNTIME -> `vite`, not `vite preview`) serves on Vite's own dev-server default port.
	// The literal IPv4 address (not "localhost") matches ProjectExecutionCapability's own
	// `--host 127.0.0.1` pin - see that class for why "localhost" alone is not reliable here.
	private static final String BASE_URL = "http://127.0.0.1:5173";
	private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
	// Generous enough to tolerate real external-network latency (an authorized/unauthorized
	// external request genuinely fetches over the internet during this smoke check) without
	// being mistaken for an infrastructure failure.
	private static final Duration NAVIGATION_TIMEOUT = Duration.ofSeconds(30);
	private static final int WIDE_WIDTH = 1280;
	private static final int WIDE_HEIGHT = 800;
	private static final int NARROW_WIDTH = 375;
	private static final int NARROW_HEIGHT = 667;

	public LocalRuntimeSmokeOutcome run(Workspace workspace) {
		Process devServer;
		try {
			devServer = new ProjectExecutionCapability(workspace).startLocalRuntime();
		} catch (RuntimeException e) {
			return LocalRuntimeSmokeOutcome.startupFailed("failed to start the local runtime: " + e.getMessage());
		}

		try {
			if (!waitUntilReady(BASE_URL, STARTUP_TIMEOUT)) {
				return LocalRuntimeSmokeOutcome.startupFailed(
						"local runtime did not become ready within " + STARTUP_TIMEOUT);
			}
			return smoke();
		} finally {
			stopEntireProcessTree(devServer);
		}
	}

	/**
	 * {@code npm run dev} forks its own child process (the actual {@code vite} server) -
	 * destroying only the {@code npm} handle leaves that child running and still bound to its
	 * port, orphaned for as long as the JVM lives. Every descendant is destroyed first, then the
	 * process itself, so nothing survives this call.
	 */
	private void stopEntireProcessTree(Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}

	private boolean waitUntilReady(String url, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			try {
				HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
				connection.setConnectTimeout(1000);
				connection.setReadTimeout(1000);
				int status = connection.getResponseCode();
				connection.disconnect();
				if (status >= 200 && status < 500) {
					return true;
				}
			} catch (IOException ignored) {
				// not ready yet - keep polling until the deadline
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private LocalRuntimeSmokeOutcome smoke() {
		List<ObservedRequest> requests = new ArrayList<>();
		List<BrowserRuntimeIssue> issues = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
			try {
				BrowserContext context = browser.newContext();
				Page page = context.newPage();
				page.onRequest(request -> requests.add(new ObservedRequest(request.url(), request.resourceType())));
				page.onConsoleMessage(message -> {
					if ("error".equals(message.type())) {
						issues.add(new BrowserRuntimeIssue("console", message.text()));
					}
				});
				page.onPageError(error -> issues.add(new BrowserRuntimeIssue("page-error", error)));
				page.onRequestFailed(request ->
						issues.add(new BrowserRuntimeIssue("request-failed", request.url() + ": " + request.failure())));

				boolean canonicalRouteLoaded = navigate(page, "/");
				Set<String> discoveredRoutes = discoverSameOriginLinks(page);
				for (String route : discoveredRoutes) {
					if (!navigate(page, route)) {
						issues.add(new BrowserRuntimeIssue("navigation", "route '" + route + "' did not load successfully"));
					}
				}

				navigate(page, "/");
				page.setViewportSize(WIDE_WIDTH, WIDE_HEIGHT);
				boolean wideOverflow = hasHorizontalOverflow(page);
				page.setViewportSize(NARROW_WIDTH, NARROW_HEIGHT);
				boolean narrowOverflow = hasHorizontalOverflow(page);

				return new LocalRuntimeSmokeOutcome(
						true, null, canonicalRouteLoaded, wideOverflow, narrowOverflow, requests, issues);
			} finally {
				browser.close();
			}
		} catch (RuntimeException e) {
			return LocalRuntimeSmokeOutcome.startupFailed("browser automation failed: " + e.getMessage());
		}
	}

	private boolean navigate(Page page, String path) {
		Response response = page.navigate(BASE_URL + path, new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT.toMillis()));
		page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(NAVIGATION_TIMEOUT.toMillis()));
		return response != null && response.ok();
	}

	@SuppressWarnings("unchecked")
	private Set<String> discoverSameOriginLinks(Page page) {
		Set<String> routes = new LinkedHashSet<>();
		Object hrefs = page.evalOnSelectorAll("a[href^='/']", "els => els.map(e => e.getAttribute('href'))");
		if (hrefs instanceof List<?> list) {
			for (Object href : list) {
				if (href instanceof String route && !route.isBlank()) {
					routes.add(route);
				}
			}
		}
		return routes;
	}

	private boolean hasHorizontalOverflow(Page page) {
		Object result = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth + 1");
		return result instanceof Boolean overflow && overflow;
	}
}
