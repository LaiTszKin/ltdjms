package ltdjms.discord.panel.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.MembershipAdminDetail;
import ltdjms.discord.membership.services.MembershipAdminService;
import ltdjms.discord.membership.services.MembershipPanelSummary;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@ExtendWith(MockitoExtension.class)
class MembershipManagementFacadeTest {

  private static final long USER_ID = 42L;
  private static final long GUILD_ID = 100L;
  private static final long ADMIN_ID = 7L;

  @Mock private MembershipAdminService membershipAdminService;

  private MembershipManagementFacade facade;

  @BeforeEach
  void setUp() {
    facade = new MembershipManagementFacade(membershipAdminService);
  }

  @Test
  @DisplayName("should delegate getDetail to admin service")
  void shouldDelegateGetDetail() {
    MembershipAdminDetail detail =
        new MembershipAdminDetail(
            new MembershipPanelSummary(
                MembershipTier.NONE, 0L, 14_000L, null, MembershipTier.NONE.discountRate(), null, 14_000L, 0),
            false);
    when(membershipAdminService.getDetail(USER_ID)).thenReturn(Result.ok(detail));

    assertThat(facade.getDetail(USER_ID).getValue()).isEqualTo(detail);
  }

  @Test
  @DisplayName("should map invalid mode to domain error")
  void shouldMapInvalidMode() {
    Result<ltdjms.discord.shared.Unit, DomainError> result =
        facade.adjustPeriodSpend(USER_ID, GUILD_ID, ADMIN_ID, "invalid", 100L);

    assertThat(result.isErr()).isTrue();
  }
}
