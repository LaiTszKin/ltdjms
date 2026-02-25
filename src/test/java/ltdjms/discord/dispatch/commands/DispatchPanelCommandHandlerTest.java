package ltdjms.discord.dispatch.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

class DispatchPanelCommandHandlerTest {

  @Test
  @DisplayName("非管理員不應該能開啟派單面板")
  void nonAdminCannotOpenDispatchPanel() {
    DispatchPanelCommandHandler handler = new DispatchPanelCommandHandler();

    SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    Guild guild = mock(Guild.class);
    Member member = mock(Member.class);
    User user = mock(User.class);
    ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);

    when(event.isFromGuild()).thenReturn(true);
    when(event.getGuild()).thenReturn(guild);
    when(event.getMember()).thenReturn(member);
    when(event.getUser()).thenReturn(user);
    when(user.getIdLong()).thenReturn(123L);
    when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
    when(guild.getOwnerIdLong()).thenReturn(999L);
    when(event.reply("你沒有權限使用派單面板")).thenReturn(replyAction);
    when(replyAction.setEphemeral(true)).thenReturn(replyAction);

    handler.handle(event);

    verify(event).reply("你沒有權限使用派單面板");
    verify(event, never()).replyEmbeds(any(MessageEmbed.class));
  }
}
