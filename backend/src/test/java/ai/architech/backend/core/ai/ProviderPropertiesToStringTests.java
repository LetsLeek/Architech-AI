package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Locks in AIW-64's toString() redaction - a real API key must never appear in a log line via either record's default toString(). */
class ProviderPropertiesToStringTests {

	@Test
	void anthropicPropertiesNeverPrintsARealKey() {
		String printed = new AnthropicProperties("sk-ant-totally-a-real-secret-key").toString();

		assertThat(printed).doesNotContain("sk-ant-totally-a-real-secret-key");
	}

	@Test
	void anthropicPropertiesDistinguishesBlankFromConfigured() {
		assertThat(new AnthropicProperties("").toString()).contains("<blank>");
		assertThat(new AnthropicProperties("sk-ant-x").toString()).contains("<redacted>");
	}

	@Test
	void openAiPropertiesNeverPrintsARealKey() {
		String printed = new OpenAiProperties("sk-totally-a-real-secret-key").toString();

		assertThat(printed).doesNotContain("sk-totally-a-real-secret-key");
	}

	@Test
	void openAiPropertiesDistinguishesBlankFromConfigured() {
		assertThat(new OpenAiProperties("").toString()).contains("<blank>");
		assertThat(new OpenAiProperties("sk-x").toString()).contains("<redacted>");
	}
}
