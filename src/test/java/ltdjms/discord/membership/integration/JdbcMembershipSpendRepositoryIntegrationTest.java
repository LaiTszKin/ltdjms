package ltdjms.discord.membership.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.integration.PostgresIntegrationTestBase;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.JdbcMembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;

/** Integration tests for {@link JdbcMembershipSpendRepository}. */
class JdbcMembershipSpendRepositoryIntegrationTest extends PostgresIntegrationTestBase {

  private static final long USER_ID = 987654321098765432L;
  private static final Instant PAID_AT = Instant.parse("2026-04-11T10:00:00Z");

  private MembershipSpendRepository spendRepository;

  @BeforeEach
  void setUp() {
    spendRepository = new JdbcMembershipSpendRepository(dataSource);
  }

  @Test
  @DisplayName("should insert spend entry idempotently by source reference")
  void shouldInsertIdempotently() {
    var first =
        spendRepository.insertSpendAndQualifyBronzeIfThreshold(
            USER_ID,
            123L,
            3500L,
            "CONF_DAM_300W",
            "FIAT_ORDER",
            "FD260411000001",
            PAID_AT,
            Long.MAX_VALUE);
    var second =
        spendRepository.insertSpendAndQualifyBronzeIfThreshold(
            USER_ID,
            123L,
            3500L,
            "CONF_DAM_300W",
            "FIAT_ORDER",
            "FD260411000001",
            PAID_AT,
            Long.MAX_VALUE);

    assertThat(first.inserted()).isTrue();
    assertThat(second.inserted()).isFalse();
    assertThat(
            spendRepository.sumListPriceInPeriod(
                USER_ID, PAID_AT.minusSeconds(1), PAID_AT.plusSeconds(1)))
        .isEqualTo(3500L);
  }

  @Test
  @DisplayName("should sum list prices within period bounds")
  void shouldSumWithinPeriod() {
    spendRepository.insertSpendAndQualifyBronzeIfThreshold(
        USER_ID, 123L, 500L, "A", "FIAT_ORDER", "ORDER-1", PAID_AT, Long.MAX_VALUE);
    spendRepository.insertSpendAndQualifyBronzeIfThreshold(
        USER_ID,
        123L,
        800L,
        "B",
        "FIAT_ORDER",
        "ORDER-2",
        PAID_AT.plusSeconds(3600),
        Long.MAX_VALUE);

    assertThat(
            spendRepository.sumListPriceInPeriod(
                USER_ID, PAID_AT.minusSeconds(1), PAID_AT.plusSeconds(1)))
        .isEqualTo(500L);
    assertThat(
            spendRepository.sumListPriceInPeriod(
                USER_ID, PAID_AT.minusSeconds(1), PAID_AT.plusSeconds(7200)))
        .isEqualTo(1300L);
  }
}
