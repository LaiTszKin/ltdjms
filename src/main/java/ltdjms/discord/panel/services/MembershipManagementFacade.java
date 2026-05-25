package ltdjms.discord.panel.services;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.MembershipAdminDetail;
import ltdjms.discord.membership.services.MembershipAdminService;
import ltdjms.discord.membership.services.SpendAdjustMode;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/** Facade for admin panel membership management operations. */
public class MembershipManagementFacade {

  private final MembershipAdminService membershipAdminService;

  public MembershipManagementFacade(MembershipAdminService membershipAdminService) {
    this.membershipAdminService = membershipAdminService;
  }

  public Result<MembershipAdminDetail, DomainError> getDetail(long userId) {
    return membershipAdminService.getDetail(userId);
  }

  public Result<Unit, DomainError> adjustPeriodSpend(
      long userId, long guildId, long adminUserId, String mode, long amountM) {
    try {
      SpendAdjustMode adjustMode = SpendAdjustMode.fromPanelMode(mode);
      return membershipAdminService.adjustPeriodSpend(
          userId, guildId, adminUserId, adjustMode, amountM);
    } catch (IllegalArgumentException e) {
      return Result.err(DomainError.invalidInput(e.getMessage()));
    }
  }

  public Result<MembershipTier, DomainError> setTier(
      long userId, long adminUserId, MembershipTier newTier) {
    return membershipAdminService.setTier(userId, adminUserId, newTier);
  }
}
