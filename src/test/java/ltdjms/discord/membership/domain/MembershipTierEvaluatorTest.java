package ltdjms.discord.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class MembershipTierEvaluatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  record TierEvaluatorCase(long avgM, boolean bronze, MembershipTier expected) {}

  static List<TierEvaluatorCase> fixtureCases() throws IOException {
    try (InputStream is =
        MembershipTierEvaluatorTest.class.getResourceAsStream(
            "/membership/tier-evaluator-cases.json")) {
      if (is == null) {
        throw new IllegalStateException("tier-evaluator-cases.json not found on classpath");
      }
      JsonNode root = MAPPER.readTree(is);
      return java.util.stream.StreamSupport.stream(root.spliterator(), false)
          .map(
              node ->
                  new TierEvaluatorCase(
                      node.get("avgM").asLong(),
                      node.get("bronze").asBoolean(),
                      MembershipTier.valueOf(node.get("expected").asText())))
          .toList();
    }
  }

  @ParameterizedTest(name = "avgM={0}, bronze={1} -> {2}")
  @MethodSource("fixtureCases")
  @DisplayName("should resolve tier from fixture cases")
  void shouldResolveTierFromFixtures(TierEvaluatorCase testCase) {
    MembershipTier tier = MembershipTierEvaluator.resolveTier(testCase.avgM(), testCase.bronze());

    assertThat(tier).isEqualTo(testCase.expected());
  }

  @Test
  @DisplayName("should treat negative avgM as zero")
  void shouldTreatNegativeAvgMAsZero() {
    assertThat(MembershipTierEvaluator.resolveTier(-100L, false)).isEqualTo(MembershipTier.NONE);
    assertThat(MembershipTierEvaluator.resolveTier(-100L, true)).isEqualTo(MembershipTier.BRONZE);
  }

  @Test
  @DisplayName("should promote NONE to BRONZE for effective tier when qualifying flag is set")
  void shouldPromoteNoneToBronzeForEffectiveTier() {
    assertThat(MembershipTierEvaluator.effectiveTier(MembershipTier.NONE, true))
        .isEqualTo(MembershipTier.BRONZE);
    assertThat(MembershipTierEvaluator.effectiveTier(MembershipTier.SILVER, true))
        .isEqualTo(MembershipTier.SILVER);
  }
}
