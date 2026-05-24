package ltdjms.discord.membership.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.currency.integration.PostgresIntegrationTestBase;
import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.JdbcMembershipRepository;
import ltdjms.discord.membership.persistence.MembershipRepository;

/** Integration tests for {@link JdbcMembershipRepository}. */
class JdbcMembershipRepositoryIntegrationTest extends PostgresIntegrationTestBase {

  private static final long TEST_USER_ID = 987654321098765432L;

  private MembershipRepository membershipRepository;

  @BeforeEach
  void setUp() {
    membershipRepository = new JdbcMembershipRepository(dataSource);
  }

  @Test
  @DisplayName("should find or create default membership")
  void shouldFindOrCreateDefaultMembership() {
    GlobalMemberMembership created = membershipRepository.findOrCreate(TEST_USER_ID);

    assertThat(created.discordUserId()).isEqualTo(TEST_USER_ID);
    assertThat(created.currentTier()).isEqualTo(MembershipTier.NONE);
    assertThat(created.hasQualifyingBronzeOrder()).isFalse();
    assertThat(created.earliestGuildJoinAt()).isNull();
    assertThat(created.settlementDayOfMonth()).isNull();

    GlobalMemberMembership found = membershipRepository.findOrCreate(TEST_USER_ID);

    assertThat(found.discordUserId()).isEqualTo(TEST_USER_ID);
    assertThat(found.currentTier()).isEqualTo(MembershipTier.NONE);
    assertThat(found.createdAt()).isEqualTo(created.createdAt());
  }

  @Test
  @DisplayName("should save membership updates")
  void shouldSaveMembershipUpdates() {
    GlobalMemberMembership created = membershipRepository.findOrCreate(TEST_USER_ID);
    Instant joinAt = Instant.parse("2026-01-15T08:00:00Z");
    Instant nextSettlement = Instant.parse("2026-02-15T08:00:00Z");

    GlobalMemberMembership updated =
        membershipRepository.save(
            new GlobalMemberMembership(
                TEST_USER_ID,
                MembershipTier.SILVER,
                joinAt,
                15,
                null,
                nextSettlement,
                true,
                created.createdAt(),
                created.updatedAt()));

    assertThat(updated.currentTier()).isEqualTo(MembershipTier.SILVER);
    assertThat(updated.earliestGuildJoinAt()).isEqualTo(joinAt);
    assertThat(updated.settlementDayOfMonth()).isEqualTo(15);
    assertThat(updated.nextSettlementAt()).isEqualTo(nextSettlement);
    assertThat(updated.hasQualifyingBronzeOrder()).isTrue();
    assertThat(updated.updatedAt()).isAfter(created.updatedAt());

    Optional<GlobalMemberMembership> persisted = membershipRepository.findByUserId(TEST_USER_ID);

    assertThat(persisted).isPresent();
    assertThat(persisted.get().currentTier()).isEqualTo(MembershipTier.SILVER);
    assertThat(persisted.get().hasQualifyingBronzeOrder()).isTrue();
    assertThat(persisted.get().settlementDayOfMonth()).isEqualTo(15);
  }

  @Test
  @DisplayName("should return empty when membership not found")
  void shouldReturnEmptyWhenNotFound() {
    assertThat(membershipRepository.findByUserId(999L)).isEmpty();
  }
}
