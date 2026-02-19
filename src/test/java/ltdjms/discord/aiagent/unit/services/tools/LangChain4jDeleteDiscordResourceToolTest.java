package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jDeleteDiscordResourceTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;

@DisplayName("LangChain4jDeleteDiscordResourceTool")
class LangChain4jDeleteDiscordResourceToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CALLER_ID = 223456789012345678L;
  private static final long TEST_CHANNEL_ID = 323456789012345678L;
  private static final long TEST_CATEGORY_ID = 423456789012345678L;
  private static final long TEST_ROLE_ID = 523456789012345678L;

  private LangChain4jDeleteDiscordResourceTool tool;
  private JDA mockJda;
  private Guild mockGuild;
  private InvocationParameters parameters;

  @BeforeEach
  void setUp() {
    tool = new LangChain4jDeleteDiscordResourceTool();
    mockJda = mock(JDA.class);
    mockGuild = mock(Guild.class);
    parameters = new InvocationParameters();

    parameters.put("guildId", TEST_GUILD_ID);
    parameters.put("channelId", TEST_CHANNEL_ID);
    parameters.put("userId", TEST_CALLER_ID);

    JDAProvider.setJda(mockJda);
    when(mockJda.getGuildById(TEST_GUILD_ID)).thenReturn(mockGuild);

    Member caller = mock(Member.class);
    when(mockGuild.getMemberById(TEST_CALLER_ID)).thenReturn(caller);
    when(caller.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Test
  @DisplayName("應成功刪除頻道")
  void shouldDeleteChannelSuccessfully() {
    GuildChannel channel = mock(GuildChannel.class);
    @SuppressWarnings("unchecked")
    AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);

    when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(channel);
    when(channel.getName()).thenReturn("general");
    when(channel.delete()).thenReturn(deleteAction);
    when(deleteAction.complete()).thenReturn(null);

    String result =
        tool.deleteDiscordResource("channel", String.valueOf(TEST_CHANNEL_ID), parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"resourceType\": \"channel\"");
    assertThat(result).contains("\"resourceId\": " + TEST_CHANNEL_ID);
    assertThat(result).contains("\"resourceName\": \"general\"");
  }

  @Test
  @DisplayName("類別含有子頻道時應拒絕刪除")
  void shouldRejectDeletingNonEmptyCategory() {
    Category category = mock(Category.class);

    when(mockGuild.getCategoryById(TEST_CATEGORY_ID)).thenReturn(category);
    when(category.getName()).thenReturn("archive");
    when(category.getChannels()).thenReturn(List.of(mock(GuildChannel.class)));

    String result =
        tool.deleteDiscordResource("category", String.valueOf(TEST_CATEGORY_ID), parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("類別內仍有頻道");
    assertThat(result).contains("\"childChannelCount\": \"1\"");
  }

  @Test
  @DisplayName("應成功刪除身分組")
  void shouldDeleteRoleSuccessfully() {
    Role role = mock(Role.class);
    @SuppressWarnings("unchecked")
    AuditableRestAction<Void> deleteAction = mock(AuditableRestAction.class);

    when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(role);
    when(role.isPublicRole()).thenReturn(false);
    when(role.isManaged()).thenReturn(false);
    when(role.getName()).thenReturn("temp-role");
    when(role.delete()).thenReturn(deleteAction);
    when(deleteAction.complete()).thenReturn(null);

    String result = tool.deleteDiscordResource("role", String.valueOf(TEST_ROLE_ID), parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("\"resourceType\": \"role\"");
    assertThat(result).contains("\"resourceId\": " + TEST_ROLE_ID);
    assertThat(result).contains("\"resourceName\": \"temp-role\"");
  }

  @Test
  @DisplayName("@everyone 身分組不可刪除")
  void shouldRejectDeletingPublicRole() {
    Role role = mock(Role.class);

    when(mockGuild.getRoleById(TEST_ROLE_ID)).thenReturn(role);
    when(role.isPublicRole()).thenReturn(true);

    String result = tool.deleteDiscordResource("role", String.valueOf(TEST_ROLE_ID), parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("@everyone 身分組不可刪除");
  }

  @Test
  @DisplayName("無效 resourceType 應返回錯誤")
  void shouldReturnErrorForInvalidResourceType() {
    String result = tool.deleteDiscordResource("member", String.valueOf(TEST_ROLE_ID), parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("resourceType 必須是 channel、category 或 role");
  }
}
