package ltdjms.discord.membership.persistence;

import java.util.Optional;

import ltdjms.discord.membership.domain.GlobalMemberMembership;

/** Persistence port for global member membership state. */
public interface MembershipRepository {

  Optional<GlobalMemberMembership> findByUserId(long discordUserId);

  GlobalMemberMembership findOrCreate(long discordUserId);

  GlobalMemberMembership save(GlobalMemberMembership membership);
}
