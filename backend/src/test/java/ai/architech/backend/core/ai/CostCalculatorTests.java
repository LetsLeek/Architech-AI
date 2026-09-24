package ai.architech.backend.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CostCalculatorTests {

	private static final AiProperties PROPERTIES = new AiProperties(
			Map.of(),
			Map.of(
					"real-provider",
					Map.of(
							"real-model",
									new ModelPricing(
											new BigDecimal("1.00"),
											new BigDecimal("2.00"),
											new BigDecimal("1.25"),
											new BigDecimal("0.10")),
							"no-cache-pricing-model",
									new ModelPricing(new BigDecimal("1.00"), new BigDecimal("2.00"), null, null))));

	private final CostCalculator calculator = new CostCalculator(PROPERTIES);

	@Test
	void calculatesCostFromConfiguredPer1000TokenPricing() {
		BigDecimal cost = calculator.calculateUsd("real-provider", "real-model", 500, 250, null, null);

		// 500 prompt tokens @ $1.00/1000 = 0.50, 250 completion tokens @ $2.00/1000 = 0.50
		assertThat(cost).isEqualByComparingTo("1.000000");
	}

	@Test
	void returnsNullWhenTokenCountsAreUnknown() {
		assertThat(calculator.calculateUsd("real-provider", "real-model", null, null, null, null)).isNull();
		assertThat(calculator.calculateUsd("real-provider", "real-model", 500, null, null, null)).isNull();
	}

	@Test
	void returnsNullWhenNoPricingIsConfiguredForTheProviderOrModel() {
		assertThat(calculator.calculateUsd("mock", "mock-model", 500, 250, null, null)).isNull();
		assertThat(calculator.calculateUsd("real-provider", "unpriced-model", 500, 250, null, null)).isNull();
	}

	@Test
	void treatsNullOrZeroCacheTokensAsNoAdditionalCostWithoutRequiringCachePricing() {
		BigDecimal withNulls = calculator.calculateUsd("real-provider", "no-cache-pricing-model", 500, 250, null, null);
		BigDecimal withZeros = calculator.calculateUsd("real-provider", "no-cache-pricing-model", 500, 250, 0, 0);

		assertThat(withNulls).isEqualByComparingTo("1.000000");
		assertThat(withZeros).isEqualByComparingTo("1.000000");
	}

	@Test
	void addsCacheWriteAndCacheReadCostWhenBothAreConfigured() {
		// 500 prompt @ $1.00/1000 = 0.50, 250 completion @ $2.00/1000 = 0.50,
		// 1000 cache-write @ $1.25/1000 = 1.25, 2000 cache-read @ $0.10/1000 = 0.20
		BigDecimal cost = calculator.calculateUsd("real-provider", "real-model", 500, 250, 1000, 2000);

		assertThat(cost).isEqualByComparingTo("2.450000");
	}

	@Test
	void returnsNullWhenCacheTokensAreReportedButTheModelHasNoCachePricingConfigured() {
		assertThat(calculator.calculateUsd("real-provider", "no-cache-pricing-model", 500, 250, 1000, null))
				.isNull();
		assertThat(calculator.calculateUsd("real-provider", "no-cache-pricing-model", 500, 250, null, 1000))
				.isNull();
	}
}
