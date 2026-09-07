package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CostCalculatorTests {

	private static final AiProperties PROPERTIES = new AiProperties(
			Map.of(),
			Map.of("real-provider", Map.of("real-model", new ModelPricing(new BigDecimal("1.00"), new BigDecimal("2.00")))));

	private final CostCalculator calculator = new CostCalculator(PROPERTIES);

	@Test
	void calculatesCostFromConfiguredPer1000TokenPricing() {
		BigDecimal cost = calculator.calculateUsd("real-provider", "real-model", 500, 250);

		// 500 prompt tokens @ $1.00/1000 = 0.50, 250 completion tokens @ $2.00/1000 = 0.50
		assertThat(cost).isEqualByComparingTo("1.000000");
	}

	@Test
	void returnsNullWhenTokenCountsAreUnknown() {
		assertThat(calculator.calculateUsd("real-provider", "real-model", null, null)).isNull();
		assertThat(calculator.calculateUsd("real-provider", "real-model", 500, null)).isNull();
	}

	@Test
	void returnsNullWhenNoPricingIsConfiguredForTheProviderOrModel() {
		assertThat(calculator.calculateUsd("mock", "mock-model", 500, 250)).isNull();
		assertThat(calculator.calculateUsd("real-provider", "unpriced-model", 500, 250)).isNull();
	}
}
