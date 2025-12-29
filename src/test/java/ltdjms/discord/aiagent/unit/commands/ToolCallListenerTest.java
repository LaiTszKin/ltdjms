package ltdjms.discord.aiagent.unit.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ltdjms.discord.aiagent.commands.ToolCallListener;
import ltdjms.discord.aiagent.domain.ToolExecutionResult;
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aiagent.services.ToolCallRequest;
import ltdjms.discord.aiagent.services.ToolExecutor;
import ltdjms.discord.shared.events.AIMessageEvent;

@DisplayName("ToolCallListener 單元測試")
class ToolCallListenerTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_CHANNEL_ID = 111111111111111111L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Test
  @DisplayName("應可解析含巢狀參數的 JSON 並提交工具")
  void shouldSubmitToolCallWhenJsonHasNestedParameters() {
    AIAgentChannelConfigService configService = mock(AIAgentChannelConfigService.class);
    when(configService.isAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID)).thenReturn(true);

    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    when(toolExecutor.submit(any()))
        .thenReturn(CompletableFuture.completedFuture(ToolExecutionResult.success("ok")));

    ToolCallListener listener = new ToolCallListener(configService, toolExecutor);

    String aiResponse =
        """
        {
          "tool": "create_channel",
          "parameters": {
            "name": "公告",
            "permissions": [
              {"roleId": 123, "permissionSet": "admin_only"}
            ]
          }
        }
        """;

    AIMessageEvent event =
        new AIMessageEvent(
            TEST_GUILD_ID,
            String.valueOf(TEST_CHANNEL_ID),
            String.valueOf(TEST_USER_ID),
            "使用者訊息",
            aiResponse,
            Instant.now());

    listener.accept(event);

    ArgumentCaptor<ToolCallRequest> captor = ArgumentCaptor.forClass(ToolCallRequest.class);
    verify(toolExecutor).submit(captor.capture());

    ToolCallRequest request = captor.getValue();
    assertThat(request.toolName()).isEqualTo("create_channel");
    assertThat(request.parameters().get("name")).isEqualTo("公告");

    Object permissions = request.parameters().get("permissions");
    assertThat(permissions).isInstanceOf(List.class);
    List<?> permissionsList = (List<?>) permissions;
    assertThat(permissionsList).hasSize(1);
    Object firstPermission = permissionsList.get(0);
    assertThat(firstPermission).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> permissionMap = (Map<String, Object>) firstPermission;
    assertThat(permissionMap).containsEntry("roleId", 123);
    assertThat(permissionMap).containsEntry("permissionSet", "admin_only");
  }

  @Test
  @DisplayName("頻道未啟用 Agent 時不應提交工具")
  void shouldNotSubmitWhenAgentDisabled() {
    AIAgentChannelConfigService configService = mock(AIAgentChannelConfigService.class);
    when(configService.isAgentEnabled(TEST_GUILD_ID, TEST_CHANNEL_ID)).thenReturn(false);

    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    ToolCallListener listener = new ToolCallListener(configService, toolExecutor);

    String aiResponse =
        """
        {"tool": "create_category", "parameters": {"name": "測試"}}
        """;

    AIMessageEvent event =
        new AIMessageEvent(
            TEST_GUILD_ID,
            String.valueOf(TEST_CHANNEL_ID),
            String.valueOf(TEST_USER_ID),
            "使用者訊息",
            aiResponse,
            Instant.now());

    listener.accept(event);

    verify(toolExecutor, never()).submit(any());
  }
}
