package ai.architech.backend.core.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * {@code RestClient.Builder} isn't auto-configured in this app (only
 * {@code spring-boot-starter-webmvc} is on the classpath, which covers the server side, not
 * Boot's REST client auto-configuration) - real {@link AiProvider}s that talk HTTP (currently
 * {@link AnthropicProvider}) need this bean to exist at all.
 */
@Configuration
class AiHttpClientConfig {

	@Bean
	RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}
}
