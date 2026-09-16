package ai.architech.backend.core.qa.checks;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Drives a real headless Chromium (Playwright for Java) against a QA execution's own bound
 * execution surface (AIW-172) - the same Playwright session pattern {@code
 * LocalRuntimeSmokeRunner} already established for Developer's own Runner Verification (launch
 * -> context -> page hooks -> navigate -> observe), generalized to accept a {@code baseUrl}
 * directly rather than provisioning a workspace/dev-server itself, since (per {@code
 * QAExecutionPreflightValidator}'s and {@code CandidateBindingValidator}'s own javadoc) no
 * durable Preview-provisioning mechanism exists in this codebase yet to obtain one from. Whatever
 * eventually solves that gap only needs to produce a URL to call {@link #observe} with - this
 * driver is already complete and real against one.
 *
 * <p>Every method here only observes; classifying an observation as a defect, an
 * undeterminable Evaluation limitation, or nothing worth reporting is each individual check's own
 * job (AIW-172's own check implementations), never this driver's.
 */
@Component
public class QaPlaywrightDriver {

	private static final Duration NAVIGATION_TIMEOUT = Duration.ofSeconds(30);

	public QaRouteObservation observe(String baseUrl, String route, int viewportWidth, int viewportHeight) {
		List<String> consoleErrors = new ArrayList<>();
		List<String> pageErrors = new ArrayList<>();
		List<String> failedRequests = new ArrayList<>();
		List<String> brokenAssetResponses = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
			try {
				BrowserContext context = browser.newContext();
				Page page = context.newPage();
				page.setViewportSize(viewportWidth, viewportHeight);
				page.onConsoleMessage(message -> {
					if ("error".equals(message.type())) {
						consoleErrors.add(message.text());
					}
				});
				page.onPageError(pageErrors::add);
				page.onRequestFailed(request -> failedRequests.add(request.url() + ": " + request.failure()));
				page.onResponse(response -> {
					if (response.status() >= 400 && isLikelyAsset(response.url())) {
						brokenAssetResponses.add(response.url() + ": " + response.status());
					}
				});

				Instant startedAt = Instant.now();
				boolean loadCompletedWithinTimeout;
				Response response = null;
				try {
					response = page.navigate(baseUrl + route, new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT.toMillis()));
					page.waitForLoadState(
							LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(NAVIGATION_TIMEOUT.toMillis()));
					loadCompletedWithinTimeout = true;
				} catch (RuntimeException e) {
					loadCompletedWithinTimeout = false;
				}
				long renderTimeMillis = Duration.between(startedAt, Instant.now()).toMillis();

				return new QaRouteObservation(
						response != null && response.ok(),
						response != null ? response.status() : 0,
						page.title(),
						(String) page.evaluate("document.documentElement.lang || ''"),
						consoleErrors,
						pageErrors,
						failedRequests,
						brokenAssetResponses,
						hasHorizontalOverflow(page),
						page.ariaSnapshot(),
						loadCompletedWithinTimeout,
						renderTimeMillis);
			} finally {
				browser.close();
			}
		}
	}

	public QaInteractionObservation interact(String baseUrl, String route, String selector) {
		List<String> consoleErrors = new ArrayList<>();
		List<String> pageErrors = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
			try {
				BrowserContext context = browser.newContext();
				Page page = context.newPage();
				page.onConsoleMessage(message -> {
					if ("error".equals(message.type())) {
						consoleErrors.add(message.text());
					}
				});
				page.onPageError(pageErrors::add);

				page.navigate(baseUrl + route, new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT.toMillis()));
				page.waitForLoadState(
						LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(NAVIGATION_TIMEOUT.toMillis()));

				Locator locator = page.locator(selector);
				if (locator.count() == 0) {
					return new QaInteractionObservation(false, false, pageErrors, consoleErrors);
				}
				try {
					locator.first().click(new Locator.ClickOptions().setTimeout(NAVIGATION_TIMEOUT.toMillis()));
					return new QaInteractionObservation(true, true, pageErrors, consoleErrors);
				} catch (RuntimeException e) {
					return new QaInteractionObservation(true, false, pageErrors, consoleErrors);
				}
			} finally {
				browser.close();
			}
		}
	}

	private boolean hasHorizontalOverflow(Page page) {
		Object result = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth + 1");
		return result instanceof Boolean overflow && overflow;
	}

	/** Playwright's own {@code resourceType()} is per-request, not per-response - a URL-extension heuristic is the pragmatic substitute for classifying a *response* as an asset. */
	private boolean isLikelyAsset(String url) {
		String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
		return path.matches(".*\\.(png|jpg|jpeg|gif|svg|webp|ico|css|js|woff2?|ttf|eot)$");
	}
}
