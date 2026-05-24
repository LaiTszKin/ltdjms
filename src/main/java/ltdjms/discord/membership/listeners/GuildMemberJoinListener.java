package ltdjms.discord.membership.listeners;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.membership.services.MembershipJoinService;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** JDA listener that records guild member joins for membership settlement anchors. */
public class GuildMemberJoinListener extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(GuildMemberJoinListener.class);

  private final MembershipJoinService membershipJoinService;

  public GuildMemberJoinListener(MembershipJoinService membershipJoinService) {
    this.membershipJoinService = membershipJoinService;
  }

  @Override
  public void onGuildMemberJoin(GuildMemberJoinEvent event) {
    long userId = event.getUser().getIdLong();
    Instant joinedAt = event.getMember().getTimeJoined().toInstant();

    try {
      membershipJoinService.onMemberJoin(userId, joinedAt);
    } catch (RuntimeException e) {
      LOG.error("Failed to record guild join for userId={}", userId, e);
    }
  }
}
