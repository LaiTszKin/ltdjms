package ltdjms.discord.membership.services;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.discord.domain.DiscordRuntimeGateway;
import ltdjms.discord.membership.domain.GlobalMemberMembership;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;

@ExtendWith(MockitoExtension.class)
class MembershipGuildJoinBackfillServiceTest {

  private static final long GUILD_ID = 111L;
  private static final long USER_ID = 222L;
  private static final Instant JOIN_AT = Instant.parse("2024-06-15T04:30:00Z");

  @Mock private MembershipRepository membershipRepository;
  @Mock private MembershipJoinService membershipJoinService;
  @Mock private DiscordRuntimeGateway discordRuntimeGateway;
  @Mock private Guild guild;
  @Mock private Member member;
  @Mock private CacheRestAction<Member> retrieveMemberAction;

  private MembershipGuildJoinBackfillService service;

  @BeforeEach
  void setUp() {
    service =
        new MembershipGuildJoinBackfillService(
            membershipRepository, membershipJoinService, discordRuntimeGateway);
  }

  @Test
  @DisplayName("should record Discord join time when membership row lacks earliest join")
  void shouldBackfillMissingJoinDate() {
    GlobalMemberMembership missingJoin = membershipWithoutJoin();
    when(membershipRepository.findByUserId(USER_ID)).thenReturn(Optional.of(missingJoin));
    when(discordRuntimeGateway.isReady()).thenReturn(true);
    when(discordRuntimeGateway.findGuild(GUILD_ID)).thenReturn(Optional.of(guild));
    doReturn(retrieveMemberAction).when(guild).retrieveMemberById(USER_ID);
    when(retrieveMemberAction.complete()).thenReturn(member);
    when(member.getTimeJoined())
        .thenReturn(OffsetDateTime.ofInstant(JOIN_AT, ZoneId.of("Asia/Taipei")));

    service.ensureRecordedFromGuild(GUILD_ID, USER_ID);

    verify(membershipJoinService).onMemberJoin(USER_ID, JOIN_AT);
  }

  @Test
  @DisplayName("should skip Discord lookup when earliest join is already stored")
  void shouldSkipWhenJoinAlreadyStored() {
    GlobalMemberMembership existing =
        new GlobalMemberMembership(
            USER_ID,
            MembershipTier.BRONZE,
            JOIN_AT,
            15,
            null,
            Instant.parse("2026-05-15T00:00:00+08:00"),
            true,
            JOIN_AT,
            JOIN_AT);
    when(membershipRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

    service.ensureRecordedFromGuild(GUILD_ID, USER_ID);

    verify(discordRuntimeGateway, never()).findGuild(GUILD_ID);
    verify(membershipJoinService, never()).onMemberJoin(eq(USER_ID), eq(JOIN_AT));
  }

  @Test
  @DisplayName("should backfill when membership row does not exist yet")
  void shouldBackfillWhenMembershipRowMissing() {
    when(membershipRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
    when(discordRuntimeGateway.isReady()).thenReturn(true);
    when(discordRuntimeGateway.findGuild(GUILD_ID)).thenReturn(Optional.of(guild));
    doReturn(retrieveMemberAction).when(guild).retrieveMemberById(USER_ID);
    when(retrieveMemberAction.complete()).thenReturn(member);
    when(member.getTimeJoined())
        .thenReturn(OffsetDateTime.ofInstant(JOIN_AT, ZoneId.of("Asia/Taipei")));

    service.ensureRecordedFromGuild(GUILD_ID, USER_ID);

    verify(membershipJoinService).onMemberJoin(USER_ID, JOIN_AT);
  }

  private static GlobalMemberMembership membershipWithoutJoin() {
    Instant now = Instant.parse("2026-03-01T00:00:00Z");
    return new GlobalMemberMembership(
        USER_ID, MembershipTier.NONE, null, null, null, null, false, now, now);
  }
}
