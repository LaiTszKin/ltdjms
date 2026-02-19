package ltdjms.discord.aiagent.unit.services.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import ltdjms.discord.aiagent.services.tools.LangChain4jMoveChannelTool;
import ltdjms.discord.shared.di.JDAProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.managers.channel.attribute.ICategorizableChannelManager;

@DisplayName("LangChain4jMoveChannelTool")
class LangChain4jMoveChannelToolTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CALLER_ID = 223456789012345678L;
  private static final long TEST_CHANNEL_ID = 323456789012345678L;
  private static final long TEST_SOURCE_CATEGORY_ID = 423456789012345678L;
  private static final long TEST_TARGET_CATEGORY_ID = 523456789012345678L;

  private LangChain4jMoveChannelTool tool;
  private JDA mockJda;
  private Guild mockGuild;
  private InvocationParameters parameters;
  private GuildChannel mockGuildChannel;
  private ICategorizableChannel mockCategorizableChannel;
  private Category mockSourceCategory;
  private Category mockTargetCategory;

  @BeforeEach
  void setUp() {
    tool = new LangChain4jMoveChannelTool();
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

    mockGuildChannel =
        mock(GuildChannel.class, withSettings().extraInterfaces(ICategorizableChannel.class));
    mockCategorizableChannel = (ICategorizableChannel) mockGuildChannel;
    mockSourceCategory = mock(Category.class);
    mockTargetCategory = mock(Category.class);

    when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(mockGuildChannel);
    when(mockGuild.getCategoryById(TEST_TARGET_CATEGORY_ID)).thenReturn(mockTargetCategory);
    when(mockGuildChannel.getName()).thenReturn("rules");
    when(mockCategorizableChannel.getParentCategory()).thenReturn(mockSourceCategory);
    when(mockSourceCategory.getIdLong()).thenReturn(TEST_SOURCE_CATEGORY_ID);
    when(mockTargetCategory.getIdLong()).thenReturn(TEST_TARGET_CATEGORY_ID);
    when(mockTargetCategory.getName()).thenReturn("support");
  }

  @AfterEach
  void tearDown() {
    JDAProvider.clear();
  }

  @Test
  @DisplayName("應成功移動頻道到目標類別")
  void shouldMoveChannelSuccessfully() {
    @SuppressWarnings("unchecked")
    ICategorizableChannelManager<?, ?> manager = mock(ICategorizableChannelManager.class);
    doReturn(manager).when(mockCategorizableChannel).getManager();
    doReturn(manager).when(manager).setParent(mockTargetCategory);
    doNothing().when(manager).complete();

    String result =
        tool.moveChannel(
            String.valueOf(TEST_CHANNEL_ID), String.valueOf(TEST_TARGET_CATEGORY_ID), parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("頻道移動成功");
    assertThat(result).contains("\"channelId\": " + TEST_CHANNEL_ID);
    assertThat(result).contains("\"targetCategoryId\": " + TEST_TARGET_CATEGORY_ID);
  }

  @Test
  @DisplayName("頻道已在目標類別時應直接返回成功")
  void shouldReturnSuccessWhenAlreadyInTargetCategory() {
    when(mockCategorizableChannel.getParentCategory()).thenReturn(mockTargetCategory);

    String result =
        tool.moveChannel(
            String.valueOf(TEST_CHANNEL_ID), String.valueOf(TEST_TARGET_CATEGORY_ID), parameters);

    assertThat(result).contains("\"success\": true");
    assertThat(result).contains("頻道已經在目標類別中");
  }

  @Test
  @DisplayName("非可分類頻道應返回錯誤")
  void shouldReturnErrorWhenChannelNotCategorizable() {
    GuildChannel plainChannel = mock(GuildChannel.class);
    when(mockGuild.getGuildChannelById(TEST_CHANNEL_ID)).thenReturn(plainChannel);

    String result =
        tool.moveChannel(
            String.valueOf(TEST_CHANNEL_ID), String.valueOf(TEST_TARGET_CATEGORY_ID), parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("該頻道類型不支援移動到類別");
  }

  @Test
  @DisplayName("目標類別不存在時應返回錯誤")
  void shouldReturnErrorWhenTargetCategoryNotFound() {
    when(mockGuild.getCategoryById(TEST_TARGET_CATEGORY_ID)).thenReturn(null);

    String result =
        tool.moveChannel(
            String.valueOf(TEST_CHANNEL_ID), String.valueOf(TEST_TARGET_CATEGORY_ID), parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("找不到指定類別");
  }

  @Test
  @DisplayName("非管理員呼叫時應拒絕")
  void shouldRejectNonAdminCaller() {
    Member nonAdmin = mock(Member.class);
    when(mockGuild.getMemberById(TEST_CALLER_ID)).thenReturn(nonAdmin);
    when(nonAdmin.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);

    String result =
        tool.moveChannel(
            String.valueOf(TEST_CHANNEL_ID), String.valueOf(TEST_TARGET_CATEGORY_ID), parameters);

    assertThat(result).contains("\"success\": false");
    assertThat(result).contains("你沒有權限使用此工具");
  }
}
