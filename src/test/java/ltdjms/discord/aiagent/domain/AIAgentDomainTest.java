package ltdjms.discord.aiagent.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.Permission;

/** Unit tests for aiagent.domain package simple classes. */
@DisplayName("AIAgent Domain")
class AIAgentDomainTest {

  @Nested
  @DisplayName("MessageRole")
  class MessageRoleTests {

    @Test
    @DisplayName("should have three role values")
    void shouldHaveThreeRoleValues() {
      // Then
      assertThat(MessageRole.values()).hasSize(3);
      assertThat(MessageRole.values())
          .containsExactly(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.TOOL);
    }

    @Test
    @DisplayName("USER role should represent user messages")
    void userRoleShouldRepresentUserMessages() {
      // Then
      assertThat(MessageRole.USER.name()).isEqualTo("USER");
    }

    @Test
    @DisplayName("ASSISTANT role should represent AI responses")
    void assistantRoleShouldRepresentAIResponses() {
      // Then
      assertThat(MessageRole.ASSISTANT.name()).isEqualTo("ASSISTANT");
    }

    @Test
    @DisplayName("TOOL role should represent tool execution results")
    void toolRoleShouldRepresentToolResults() {
      // Then
      assertThat(MessageRole.TOOL.name()).isEqualTo("TOOL");
    }
  }

  @Nested
  @DisplayName("ChannelPermission")
  class ChannelPermissionTests {

    @Test
    @DisplayName("should create ChannelPermission successfully")
    void shouldCreateChannelPermission() {
      // Given
      EnumSet<Permission> permissions =
          EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND);

      // When
      ChannelPermission permission = new ChannelPermission(123L, permissions);

      // Then
      assertThat(permission.roleId()).isEqualTo(123L);
      assertThat(permission.permissionSet()).isEqualTo(permissions);
    }

    @Test
    @DisplayName("should throw exception when permissionSet is null")
    void shouldThrowExceptionWhenPermissionSetNull() {
      // When/Then
      assertThatThrownBy(() -> new ChannelPermission(123L, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("permissionSet must not be null");
    }

    @Test
    @DisplayName("should create readOnly permission")
    void shouldCreateReadOnlyPermission() {
      // When
      ChannelPermission permission = ChannelPermission.readOnly(456L);

      // Then
      assertThat(permission.roleId()).isEqualTo(456L);
      assertThat(permission.permissionSet()).containsExactly(Permission.VIEW_CHANNEL);
    }

    @Test
    @DisplayName("should create fullAccess permission")
    void shouldCreateFullAccessPermission() {
      // When
      ChannelPermission permission = ChannelPermission.fullAccess(789L);

      // Then
      assertThat(permission.roleId()).isEqualTo(789L);
      assertThat(permission.permissionSet()).contains(Permission.values());
    }
  }

  @Nested
  @DisplayName("PermissionSetting")
  class PermissionSettingTests {

    @Test
    @DisplayName("should create PermissionSetting successfully")
    void shouldCreatePermissionSetting() {
      // Given
      Set<PermissionSetting.PermissionEnum> allowSet =
          Set.of(
              PermissionSetting.PermissionEnum.VIEW_CHANNEL,
              PermissionSetting.PermissionEnum.MESSAGE_SEND);
      Set<PermissionSetting.PermissionEnum> denySet =
          Set.of(PermissionSetting.PermissionEnum.ADMINISTRATOR);

      // When
      PermissionSetting setting = new PermissionSetting(123L, allowSet, denySet);

      // Then
      assertThat(setting.roleId()).isEqualTo(123L);
      assertThat(setting.allowSet()).isEqualTo(allowSet);
      assertThat(setting.denySet()).isEqualTo(denySet);
    }

    @Test
    @DisplayName("should create PermissionSetting with null sets")
    void shouldCreatePermissionSettingWithNullSets() {
      // When
      PermissionSetting setting = new PermissionSetting(123L, null, null);

      // Then
      assertThat(setting.roleId()).isEqualTo(123L);
      assertThat(setting.allowSet()).isEmpty();
      assertThat(setting.denySet()).isEmpty();
    }

    @Test
    @DisplayName("should parse admin_only permissionSet string")
    void shouldParseAdminOnlyPermissionSet() {
      // When
      PermissionSetting setting = PermissionSetting.fromJson(123L, null, null, "admin_only");

      // Then
      assertThat(setting.roleId()).isEqualTo(123L);
      assertThat(setting.allowSet())
          .containsExactlyInAnyOrder(
              PermissionSetting.PermissionEnum.ADMINISTRATOR,
              PermissionSetting.PermissionEnum.VIEW_CHANNEL,
              PermissionSetting.PermissionEnum.MESSAGE_SEND);
      assertThat(setting.denySet()).isEmpty();
    }

    @Test
    @DisplayName("should parse private permissionSet string")
    void shouldParsePrivatePermissionSet() {
      // When
      PermissionSetting setting = PermissionSetting.fromJson(456L, null, null, "private");

      // Then
      assertThat(setting.roleId()).isEqualTo(456L);
      assertThat(setting.allowSet())
          .containsExactlyInAnyOrder(
              PermissionSetting.PermissionEnum.VIEW_CHANNEL,
              PermissionSetting.PermissionEnum.MESSAGE_SEND);
    }

    @Test
    @DisplayName("should parse read_only permissionSet string")
    void shouldParseReadOnlyPermissionSet() {
      // When
      PermissionSetting setting = PermissionSetting.fromJson(789L, null, null, "read_only");

      // Then
      assertThat(setting.roleId()).isEqualTo(789L);
      assertThat(setting.allowSet()).containsExactly(PermissionSetting.PermissionEnum.VIEW_CHANNEL);
    }

    @Test
    @DisplayName("should parse full permissionSet string")
    void shouldParseFullPermissionSet() {
      // When
      PermissionSetting setting = PermissionSetting.fromJson(101L, null, null, "full");

      // Then
      assertThat(setting.roleId()).isEqualTo(101L);
      assertThat(setting.allowSet())
          .contains(
              PermissionSetting.PermissionEnum.ADMINISTRATOR,
              PermissionSetting.PermissionEnum.MANAGE_CHANNELS,
              PermissionSetting.PermissionEnum.MANAGE_ROLES,
              PermissionSetting.PermissionEnum.MANAGE_SERVER,
              PermissionSetting.PermissionEnum.VIEW_CHANNEL,
              PermissionSetting.PermissionEnum.MESSAGE_SEND,
              PermissionSetting.PermissionEnum.MESSAGE_HISTORY,
              PermissionSetting.PermissionEnum.VOICE_CONNECT,
              PermissionSetting.PermissionEnum.VOICE_SPEAK,
              PermissionSetting.PermissionEnum.PRIORITY_SPEAKER);
    }

    @Test
    @DisplayName("should prefer allowSet over permissionSet string")
    void shouldPreferAllowSetOverPermissionSetString() {
      // Given
      Set<PermissionSetting.PermissionEnum> allowSet =
          Set.of(PermissionSetting.PermissionEnum.MESSAGE_SEND);

      // When
      PermissionSetting setting = PermissionSetting.fromJson(123L, allowSet, null, "admin_only");

      // Then
      assertThat(setting.allowSet()).containsExactly(PermissionSetting.PermissionEnum.MESSAGE_SEND);
    }

    @Test
    @DisplayName("should handle case-insensitive permissionSet strings")
    void shouldHandleCaseInsensitivePermissionSetStrings() {
      // When - lowercase
      PermissionSetting setting1 = PermissionSetting.fromJson(123L, null, null, "private");

      // Then
      assertThat(setting1.allowSet()).isNotEmpty();

      // When - uppercase
      PermissionSetting setting2 = PermissionSetting.fromJson(456L, null, null, "READ_ONLY");

      // Then
      assertThat(setting2.allowSet())
          .containsExactly(PermissionSetting.PermissionEnum.VIEW_CHANNEL);
    }

    @Test
    @DisplayName("should return empty allowSet for unknown permissionSet string")
    void shouldReturnEmptyAllowSetForUnknownString() {
      // When
      PermissionSetting setting =
          PermissionSetting.fromJson(123L, null, null, "unknown_permission");

      // Then
      assertThat(setting.allowSet()).isEmpty();
    }
  }

  @Nested
  @DisplayName("PermissionEnum")
  class PermissionEnumTests {

    @Test
    @DisplayName("should have all permission values")
    void shouldHaveAllPermissionValues() {
      // Then
      assertThat(PermissionSetting.PermissionEnum.values()).hasSize(10);
    }

    @Test
    @DisplayName("should contain ADMINISTRATOR")
    void shouldContainAdministrator() {
      assertThat(PermissionSetting.PermissionEnum.ADMINISTRATOR.name()).isEqualTo("ADMINISTRATOR");
    }

    @Test
    @DisplayName("should contain MANAGE_CHANNELS")
    void shouldContainManageChannels() {
      assertThat(PermissionSetting.PermissionEnum.MANAGE_CHANNELS.name())
          .isEqualTo("MANAGE_CHANNELS");
    }

    @Test
    @DisplayName("should contain VIEW_CHANNEL")
    void shouldContainViewChannel() {
      assertThat(PermissionSetting.PermissionEnum.VIEW_CHANNEL.name()).isEqualTo("VIEW_CHANNEL");
    }

    @Test
    @DisplayName("should contain MESSAGE_SEND")
    void shouldContainMessageSend() {
      assertThat(PermissionSetting.PermissionEnum.MESSAGE_SEND.name()).isEqualTo("MESSAGE_SEND");
    }
  }

  @Nested
  @DisplayName("ConversationIdStrategy")
  class ConversationIdStrategyTests {

    @Test
    @DisplayName("should have MESSAGE_LEVEL value")
    void shouldHaveMessageLevelValue() {
      assertThat(ConversationIdStrategy.MESSAGE_LEVEL.name()).isEqualTo("MESSAGE_LEVEL");
    }

    @Test
    @DisplayName("should have THREAD_LEVEL value")
    void shouldHaveThreadLevelValue() {
      assertThat(ConversationIdStrategy.THREAD_LEVEL.name()).isEqualTo("THREAD_LEVEL");
    }
  }

  @Nested
  @DisplayName("ToolExecutionResult")
  class ToolExecutionResultTests {

    @Test
    @DisplayName("should create success result")
    void shouldCreateSuccessResult() {
      // When
      ToolExecutionResult result = ToolExecutionResult.success("Operation completed");

      // Then
      assertThat(result.success()).isTrue();
      assertThat(result.result()).hasValue("Operation completed");
      assertThat(result.error()).isEmpty();
    }

    @Test
    @DisplayName("should create failure result")
    void shouldCreateFailureResult() {
      // When
      ToolExecutionResult result = ToolExecutionResult.failure("Operation failed");

      // Then
      assertThat(result.success()).isFalse();
      assertThat(result.result()).isEmpty();
      assertThat(result.error()).hasValue("Operation failed");
    }

    @Test
    @DisplayName("should create result with all fields")
    void shouldCreateResultWithAllFields() {
      // When
      ToolExecutionResult result =
          new ToolExecutionResult(
              true, java.util.Optional.of("data"), java.util.Optional.of("warning"));

      // Then
      assertThat(result.success()).isTrue();
      assertThat(result.result()).hasValue("data");
      assertThat(result.error()).hasValue("warning");
    }
  }

  @Nested
  @DisplayName("ToolParameter.ParamType")
  class ToolParameterParamTypeTests {

    @Test
    @DisplayName("should have STRING type")
    void shouldHaveStringType() {
      assertThat(ToolParameter.ParamType.STRING.name()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("should have NUMBER type")
    void shouldHaveNumberType() {
      assertThat(ToolParameter.ParamType.NUMBER.name()).isEqualTo("NUMBER");
    }

    @Test
    @DisplayName("should have BOOLEAN type")
    void shouldHaveBooleanType() {
      assertThat(ToolParameter.ParamType.BOOLEAN.name()).isEqualTo("BOOLEAN");
    }

    @Test
    @DisplayName("should have ARRAY type")
    void shouldHaveArrayType() {
      assertThat(ToolParameter.ParamType.ARRAY.name()).isEqualTo("ARRAY");
    }

    @Test
    @DisplayName("should have OBJECT type")
    void shouldHaveObjectType() {
      assertThat(ToolParameter.ParamType.OBJECT.name()).isEqualTo("OBJECT");
    }
  }

  @Nested
  @DisplayName("ToolParameter")
  class ToolParameterTests {

    @Test
    @DisplayName("should create ToolParameter successfully")
    void shouldCreateToolParameter() {
      // When
      ToolParameter param =
          new ToolParameter("test", ToolParameter.ParamType.STRING, "description", false, null);

      // Then
      assertThat(param.name()).isEqualTo("test");
      assertThat(param.type()).isEqualTo(ToolParameter.ParamType.STRING);
      assertThat(param.description()).isEqualTo("description");
      assertThat(param.required()).isFalse();
      assertThat(param.defaultValue()).isNull();
    }

    @Test
    @DisplayName("should throw exception when name is null")
    void shouldThrowExceptionWhenNameNull() {
      // When/Then
      assertThatThrownBy(
              () -> new ToolParameter(null, ToolParameter.ParamType.STRING, "desc", false, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("name must not be null");
    }

    @Test
    @DisplayName("should throw exception when type is null")
    void shouldThrowExceptionWhenTypeNull() {
      // When/Then
      assertThatThrownBy(() -> new ToolParameter("test", null, "desc", false, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("type must not be null");
    }

    @Test
    @DisplayName("should throw exception when description is null")
    void shouldThrowExceptionWhenDescriptionNull() {
      // When/Then
      assertThatThrownBy(
              () -> new ToolParameter("test", ToolParameter.ParamType.STRING, null, false, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("description must not be null");
    }

    @Test
    @DisplayName("should convert to JSON property")
    void shouldConvertToJsonProperty() {
      // Given
      ToolParameter param =
          new ToolParameter("param1", ToolParameter.ParamType.STRING, "Test parameter", true, null);

      // When
      String json = param.toJsonProperty();

      // Then
      assertThat(json).contains("\"param1\"");
      assertThat(json).contains("\"string\"");
      assertThat(json).contains("Test parameter");
    }
  }

  @Nested
  @DisplayName("ToolDefinition")
  class ToolDefinitionTests {

    @Test
    @DisplayName("should create ToolDefinition successfully")
    void shouldCreateToolDefinition() {
      // Given
      ToolParameter param =
          new ToolParameter("name", ToolParameter.ParamType.STRING, "Channel name", true, null);

      // When
      ToolDefinition tool =
          new ToolDefinition("create_channel", "Create a channel", List.of(param));

      // Then
      assertThat(tool.name()).isEqualTo("create_channel");
      assertThat(tool.description()).isEqualTo("Create a channel");
      assertThat(tool.parameters()).hasSize(1);
    }

    @Test
    @DisplayName("should throw exception when name is blank")
    void shouldThrowExceptionWhenNameBlank() {
      // Given
      ToolParameter param =
          new ToolParameter("name", ToolParameter.ParamType.STRING, "desc", true, null);

      // When/Then
      assertThatThrownBy(() -> new ToolDefinition("", "desc", List.of(param)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("工具名稱不能為空");
    }

    @Test
    @DisplayName("should throw exception when description is blank")
    void shouldThrowExceptionWhenDescriptionBlank() {
      // Given
      ToolParameter param =
          new ToolParameter("name", ToolParameter.ParamType.STRING, "desc", true, null);

      // When/Then
      assertThatThrownBy(() -> new ToolDefinition("tool", "", List.of(param)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("工具描述不能為空");
    }

    @Test
    @DisplayName("should throw exception when name is null")
    void shouldThrowExceptionWhenNameNull() {
      // When/Then
      assertThatThrownBy(() -> new ToolDefinition(null, "desc", List.of()))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("name must not be null");
    }

    @Test
    @DisplayName("should convert to JSON schema")
    void shouldConvertToJsonSchema() {
      // Given
      ToolParameter param1 =
          new ToolParameter("name", ToolParameter.ParamType.STRING, "Channel name", true, null);
      ToolParameter param2 =
          new ToolParameter(
              "permissions", ToolParameter.ParamType.ARRAY, "Permissions", false, null);
      ToolDefinition tool =
          new ToolDefinition("create_channel", "Create channel", List.of(param1, param2));

      // When
      String schema = tool.toJsonSchema();

      // Then
      assertThat(schema).contains("\"name\": \"create_channel\"");
      assertThat(schema).contains("\"description\": \"Create channel\"");
      assertThat(schema).contains("\"type\": \"object\"");
      assertThat(schema).contains("\"required\": [\"name\"]");
    }
  }

  @Nested
  @DisplayName("AgentConversation")
  class AgentConversationTests {

    @Test
    @DisplayName("should create AgentConversation successfully")
    void shouldCreateAgentConversation() {
      // Given
      java.time.Instant now = java.time.Instant.now();
      List<ConversationMessage> history = List.of();

      // When
      AgentConversation conversation =
          new AgentConversation("conv-id", 123L, 456L, null, 789L, 999L, history, 0, now, now);

      // Then
      assertThat(conversation.conversationId()).isEqualTo("conv-id");
      assertThat(conversation.guildId()).isEqualTo(123L);
      assertThat(conversation.channelId()).isEqualTo(456L);
      assertThat(conversation.threadId()).isNull();
      assertThat(conversation.userId()).isEqualTo(789L);
      assertThat(conversation.originalMessageId()).isEqualTo(999L);
      assertThat(conversation.iterationCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should add message with withMessage")
    void shouldAddMessageWithWithMessage() {
      // Given
      java.time.Instant now = java.time.Instant.now();
      List<ConversationMessage> history = List.of();
      AgentConversation conversation =
          new AgentConversation("conv-id", 123L, 456L, null, 789L, 999L, history, 0, now, now);
      ConversationMessage newMessage =
          new ConversationMessage(
              MessageRole.ASSISTANT, "response", now, java.util.Optional.empty());

      // When
      AgentConversation updated = conversation.withMessage(newMessage);

      // Then
      assertThat(updated.iterationCount()).isEqualTo(1);
      assertThat(updated.history()).hasSize(1);
      assertThat(updated.history().get(0)).isEqualTo(newMessage);
    }

    @Test
    @DisplayName("should return false when iteration count below max")
    void shouldReturnFalseWhenIterationBelowMax() {
      // Given
      java.time.Instant now = java.time.Instant.now();
      List<ConversationMessage> history = List.of();
      AgentConversation conversation =
          new AgentConversation("conv-id", 123L, 456L, null, 789L, 999L, history, 3, now, now);

      // When
      boolean result = conversation.hasReachedMaxIterations();

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should return true when iteration count reaches max")
    void shouldReturnTrueWhenIterationReachesMax() {
      // Given
      java.time.Instant now = java.time.Instant.now();
      List<ConversationMessage> history = List.of();
      AgentConversation conversation =
          new AgentConversation("conv-id", 123L, 456L, null, 789L, 999L, history, 5, now, now);

      // When
      boolean result = conversation.hasReachedMaxIterations();

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should have MAX_ITERATIONS constant")
    void shouldHaveMaxIterationsConstant() {
      // Then
      assertThat(AgentConversation.MAX_ITERATIONS).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("AIAgentTools")
  class AIAgentToolsTests {

    @Test
    @DisplayName("should return all tools")
    void shouldReturnAllTools() {
      // When
      List<ToolDefinition> tools = AIAgentTools.all();

      // Then
      assertThat(tools).hasSize(8);
      assertThat(tools.get(0).name()).isEqualTo("create_channel");
      assertThat(tools.get(1).name()).isEqualTo("create_category");
      assertThat(tools.get(2).name()).isEqualTo("list_channels");
      assertThat(tools.get(3).name()).isEqualTo("move_channel");
      assertThat(tools.get(4).name()).isEqualTo("send_messages");
      assertThat(tools.get(5).name()).isEqualTo("search_messages");
      assertThat(tools.get(6).name()).isEqualTo("manage_message");
      assertThat(tools.get(7).name()).isEqualTo("delete_discord_resource");
    }

    @Test
    @DisplayName("should have CREATE_CHANNEL tool")
    void shouldHaveCreateChannelTool() {
      // Then
      assertThat(AIAgentTools.CREATE_CHANNEL.name()).isEqualTo("create_channel");
      assertThat(AIAgentTools.CREATE_CHANNEL.description()).contains("頻道");
      assertThat(AIAgentTools.CREATE_CHANNEL.parameters()).hasSize(2);
    }

    @Test
    @DisplayName("should have CREATE_CATEGORY tool")
    void shouldHaveCreateCategoryTool() {
      // Then
      assertThat(AIAgentTools.CREATE_CATEGORY.name()).isEqualTo("create_category");
      assertThat(AIAgentTools.CREATE_CATEGORY.description()).contains("類別");
    }

    @Test
    @DisplayName("should have LIST_CHANNELS tool")
    void shouldHaveListChannelsTool() {
      // Then
      assertThat(AIAgentTools.LIST_CHANNELS.name()).isEqualTo("list_channels");
      assertThat(AIAgentTools.LIST_CHANNELS.description()).contains("頻道");
    }

    @Test
    @DisplayName("should have MOVE_CHANNEL tool")
    void shouldHaveMoveChannelTool() {
      // Then
      assertThat(AIAgentTools.MOVE_CHANNEL.name()).isEqualTo("move_channel");
      assertThat(AIAgentTools.MOVE_CHANNEL.description()).contains("移動");
      assertThat(AIAgentTools.MOVE_CHANNEL.parameters()).hasSize(2);
    }

    @Test
    @DisplayName("should have SEND_MESSAGES tool")
    void shouldHaveSendMessagesTool() {
      // Then
      assertThat(AIAgentTools.SEND_MESSAGES.name()).isEqualTo("send_messages");
      assertThat(AIAgentTools.SEND_MESSAGES.description()).contains("訊息");
      assertThat(AIAgentTools.SEND_MESSAGES.parameters()).hasSize(3);
    }

    @Test
    @DisplayName("should have SEARCH_MESSAGES tool")
    void shouldHaveSearchMessagesTool() {
      // Then
      assertThat(AIAgentTools.SEARCH_MESSAGES.name()).isEqualTo("search_messages");
      assertThat(AIAgentTools.SEARCH_MESSAGES.description()).contains("搜尋");
      assertThat(AIAgentTools.SEARCH_MESSAGES.parameters()).hasSize(4);
    }

    @Test
    @DisplayName("should have MANAGE_MESSAGE tool")
    void shouldHaveManageMessageTool() {
      // Then
      assertThat(AIAgentTools.MANAGE_MESSAGE.name()).isEqualTo("manage_message");
      assertThat(AIAgentTools.MANAGE_MESSAGE.description()).contains("訊息");
      assertThat(AIAgentTools.MANAGE_MESSAGE.parameters()).hasSize(5);
    }

    @Test
    @DisplayName("should have DELETE_DISCORD_RESOURCE tool")
    void shouldHaveDeleteDiscordResourceTool() {
      // Then
      assertThat(AIAgentTools.DELETE_DISCORD_RESOURCE.name()).isEqualTo("delete_discord_resource");
      assertThat(AIAgentTools.DELETE_DISCORD_RESOURCE.description()).contains("刪除");
      assertThat(AIAgentTools.DELETE_DISCORD_RESOURCE.parameters()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("ToolCallInfo")
  class ToolCallInfoTests {

    @Test
    @DisplayName("should create ToolCallInfo successfully")
    void shouldCreateToolCallInfo() {
      // Given
      Map<String, Object> params = Map.of("channelId", 123L, "name", "test-channel");

      // When
      ToolCallInfo info = new ToolCallInfo("create_channel", params, true, "Channel created");

      // Then
      assertThat(info.toolName()).isEqualTo("create_channel");
      assertThat(info.parameters()).isEqualTo(params);
      assertThat(info.success()).isTrue();
      assertThat(info.result()).isEqualTo("Channel created");
    }

    @Test
    @DisplayName("should create failed ToolCallInfo")
    void shouldCreateFailedToolCallInfo() {
      // When
      ToolCallInfo info = new ToolCallInfo("create_channel", Map.of(), false, "Permission denied");

      // Then
      assertThat(info.success()).isFalse();
      assertThat(info.result()).isEqualTo("Permission denied");
    }
  }

  @Nested
  @DisplayName("ConversationIdBuilder")
  class ConversationIdBuilderTests {

    @Test
    @DisplayName("should build message level conversation ID")
    void shouldBuildMessageLevelConversationId() {
      // When
      String id = ConversationIdBuilder.build(123L, 456L, null, 789L, 101112L);

      // Then
      assertThat(id).isEqualTo("123:456:789:101112");
    }

    @Test
    @DisplayName("should build thread level conversation ID")
    void shouldBuildThreadLevelConversationId() {
      // When
      String id = ConversationIdBuilder.build(123L, 456L, 999L, 789L, 101112L);

      // Then
      assertThat(id).isEqualTo("123:999:789");
    }

    @Test
    @DisplayName("should parse thread level strategy")
    void shouldParseThreadLevelStrategy() {
      // Given
      String id = "123:999:789";

      // When
      ConversationIdStrategy strategy = ConversationIdBuilder.parseStrategy(id);

      // Then
      assertThat(strategy).isEqualTo(ConversationIdStrategy.THREAD_LEVEL);
    }

    @Test
    @DisplayName("should parse message level strategy")
    void shouldParseMessageLevelStrategy() {
      // Given
      String id = "123:456:789:101112";

      // When
      ConversationIdStrategy strategy = ConversationIdBuilder.parseStrategy(id);

      // Then
      assertThat(strategy).isEqualTo(ConversationIdStrategy.MESSAGE_LEVEL);
    }

    @Test
    @DisplayName("should throw exception for invalid conversation ID format")
    void shouldThrowExceptionForInvalidFormat() {
      // Given
      String id = "123:456";

      // When/Then
      assertThatThrownBy(() -> ConversationIdBuilder.parseStrategy(id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("無效的會話 ID 格式");
    }

    @Test
    @DisplayName("should reject thread-level conversation ID with trailing delimiter")
    void shouldRejectThreadLevelConversationIdWithTrailingDelimiter() {
      // Given
      String id = "123:999:789:";

      // When/Then
      assertThatThrownBy(() -> ConversationIdBuilder.parseStrategy(id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("無效的會話 ID 格式");
    }

    @Test
    @DisplayName("should reject conversation ID containing empty segment")
    void shouldRejectConversationIdContainingEmptySegment() {
      // Given
      String id = "123::789";

      // When/Then
      assertThatThrownBy(() -> ConversationIdBuilder.parseStrategy(id))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("無效的會話 ID 格式");
    }

    @Test
    @DisplayName("should throw exception for null conversation ID")
    void shouldThrowExceptionForNullConversationId() {
      // When/Then
      assertThatThrownBy(() -> ConversationIdBuilder.parseStrategy(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("會話 ID 不能為空");
    }

    @Test
    @DisplayName("should throw exception for blank conversation ID")
    void shouldThrowExceptionForBlankConversationId() {
      // When/Then
      assertThatThrownBy(() -> ConversationIdBuilder.parseStrategy("  "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("會話 ID 不能為空");
    }

    @Test
    @DisplayName("should return true for thread level conversation ID")
    void shouldReturnTrueForThreadLevel() {
      // Given
      String id = "123:999:789";

      // When
      boolean result = ConversationIdBuilder.isThreadLevel(id);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return false for message level conversation ID")
    void shouldReturnFalseForMessageLevel() {
      // Given
      String id = "123:456:789:101112";

      // When
      boolean result = ConversationIdBuilder.isThreadLevel(id);

      // Then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("ConversationMessage")
  class ConversationMessageTests {

    @Test
    @DisplayName("should create message with all fields")
    void shouldCreateMessageWithAllFields() {
      // Given
      java.time.Instant now = java.time.Instant.now();
      ToolCallInfo toolCall = new ToolCallInfo("test", Map.of(), true, "result");

      // When
      ConversationMessage message =
          new ConversationMessage(
              MessageRole.TOOL,
              "result",
              now,
              java.util.Optional.of(toolCall),
              java.util.Optional.of("reasoning"));

      // Then
      assertThat(message.role()).isEqualTo(MessageRole.TOOL);
      assertThat(message.content()).isEqualTo("result");
      assertThat(message.timestamp()).isEqualTo(now);
      assertThat(message.toolCall()).hasValue(toolCall);
      assertThat(message.reasoningContent()).hasValue("reasoning");
    }

    @Test
    @DisplayName("should create message without reasoning content")
    void shouldCreateMessageWithoutReasoningContent() {
      // Given
      java.time.Instant now = java.time.Instant.now();

      // When
      ConversationMessage message =
          new ConversationMessage(MessageRole.USER, "hello", now, java.util.Optional.empty());

      // Then
      assertThat(message.role()).isEqualTo(MessageRole.USER);
      assertThat(message.reasoningContent()).isEmpty();
    }

    @Test
    @DisplayName("should create message with tool call")
    void shouldCreateMessageWithToolCall() {
      // Given
      java.time.Instant now = java.time.Instant.now();
      ToolCallInfo toolCall =
          new ToolCallInfo("tool_name", Map.of("key", "value"), true, "success");

      // When
      ConversationMessage message =
          new ConversationMessage(
              MessageRole.TOOL, "Tool result", now, java.util.Optional.of(toolCall));

      // Then
      assertThat(message.toolCall()).hasValue(toolCall);
      assertThat(message.reasoningContent()).isEmpty();
    }

    @Test
    @DisplayName("should create assistant message with reasoning")
    void shouldCreateAssistantMessageWithReasoning() {
      // Given
      java.time.Instant now = java.time.Instant.now();

      // When
      ConversationMessage message =
          new ConversationMessage(
              MessageRole.ASSISTANT,
              "response",
              now,
              java.util.Optional.empty(),
              java.util.Optional.of("thinking..."));

      // Then
      assertThat(message.role()).isEqualTo(MessageRole.ASSISTANT);
      assertThat(message.reasoningContent()).hasValue("thinking...");
    }
  }

  @Nested
  @DisplayName("ToolExecutionLog")
  class ToolExecutionLogTests {

    @Test
    @DisplayName("should create ToolExecutionLog with all fields")
    void shouldCreateToolExecutionLog() {
      // Given
      java.time.LocalDateTime now = java.time.LocalDateTime.now();

      // When
      ToolExecutionLog log =
          new ToolExecutionLog(
              1L,
              123L,
              456L,
              789L,
              "create_channel",
              "{\"channelId\":123}",
              "success",
              null,
              ToolExecutionLog.ExecutionStatus.SUCCESS,
              now);

      // Then
      assertThat(log.id()).isEqualTo(1L);
      assertThat(log.guildId()).isEqualTo(123L);
      assertThat(log.channelId()).isEqualTo(456L);
      assertThat(log.triggerUserId()).isEqualTo(789L);
      assertThat(log.toolName()).isEqualTo("create_channel");
      assertThat(log.status()).isEqualTo(ToolExecutionLog.ExecutionStatus.SUCCESS);
    }

    @Test
    @DisplayName("should throw exception when toolName is null")
    void shouldThrowExceptionWhenToolNameNull() {
      // Given
      java.time.LocalDateTime now = java.time.LocalDateTime.now();

      // When/Then
      assertThatThrownBy(
              () ->
                  new ToolExecutionLog(
                      1L,
                      123L,
                      456L,
                      789L,
                      null,
                      "{}",
                      null,
                      null,
                      ToolExecutionLog.ExecutionStatus.SUCCESS,
                      now))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("toolName must not be null");
    }

    @Test
    @DisplayName("should throw exception when parameters is null")
    void shouldThrowExceptionWhenParametersNull() {
      // Given
      java.time.LocalDateTime now = java.time.LocalDateTime.now();

      // When/Then
      assertThatThrownBy(
              () ->
                  new ToolExecutionLog(
                      1L,
                      123L,
                      456L,
                      789L,
                      "tool",
                      null,
                      null,
                      null,
                      ToolExecutionLog.ExecutionStatus.SUCCESS,
                      now))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("parameters must not be null");
    }

    @Test
    @DisplayName("should throw exception when status is null")
    void shouldThrowExceptionWhenStatusNull() {
      // Given
      java.time.LocalDateTime now = java.time.LocalDateTime.now();

      // When/Then
      assertThatThrownBy(
              () -> new ToolExecutionLog(1L, 123L, 456L, 789L, "tool", "{}", null, null, null, now))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("status must not be null");
    }

    @Test
    @DisplayName("should throw exception when executedAt is null")
    void shouldThrowExceptionWhenExecutedAtNull() {
      // When/Then
      assertThatThrownBy(
              () ->
                  new ToolExecutionLog(
                      1L,
                      123L,
                      456L,
                      789L,
                      "tool",
                      "{}",
                      null,
                      null,
                      ToolExecutionLog.ExecutionStatus.SUCCESS,
                      null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("executedAt must not be null");
    }

    @Test
    @DisplayName("should create success log using factory method")
    void shouldCreateSuccessLogUsingFactoryMethod() {
      // When
      ToolExecutionLog log =
          ToolExecutionLog.success(
              123L, 456L, 789L, "create_channel", "{\"name\":\"test\"}", "Channel created");

      // Then
      assertThat(log.guildId()).isEqualTo(123L);
      assertThat(log.channelId()).isEqualTo(456L);
      assertThat(log.triggerUserId()).isEqualTo(789L);
      assertThat(log.toolName()).isEqualTo("create_channel");
      assertThat(log.executionResult()).isEqualTo("Channel created");
      assertThat(log.errorMessage()).isNull();
      assertThat(log.status()).isEqualTo(ToolExecutionLog.ExecutionStatus.SUCCESS);
    }

    @Test
    @DisplayName("should create failure log using factory method")
    void shouldCreateFailureLogUsingFactoryMethod() {
      // When
      ToolExecutionLog log =
          ToolExecutionLog.failure(
              123L, 456L, 789L, "create_channel", "{\"name\":\"test\"}", "Permission denied");

      // Then
      assertThat(log.guildId()).isEqualTo(123L);
      assertThat(log.channelId()).isEqualTo(456L);
      assertThat(log.triggerUserId()).isEqualTo(789L);
      assertThat(log.toolName()).isEqualTo("create_channel");
      assertThat(log.executionResult()).isNull();
      assertThat(log.errorMessage()).isEqualTo("Permission denied");
      assertThat(log.status()).isEqualTo(ToolExecutionLog.ExecutionStatus.FAILED);
    }

    @Test
    @DisplayName("should have SUCCESS enum value")
    void shouldHaveSuccessEnumValue() {
      assertThat(ToolExecutionLog.ExecutionStatus.SUCCESS.name()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("should have FAILED enum value")
    void shouldHaveFailedEnumValue() {
      assertThat(ToolExecutionLog.ExecutionStatus.FAILED.name()).isEqualTo("FAILED");
    }
  }

  @Nested
  @DisplayName("ModifyPermissionSetting")
  class ModifyPermissionSettingTests {

    @Test
    @DisplayName("should create ModifyPermissionSetting successfully")
    void shouldCreateModifyPermissionSetting() {
      // Given
      var allowToAdd = Set.of(ModifyPermissionSetting.PermissionEnum.VIEW_CHANNEL);
      var denyToAdd = Set.of(ModifyPermissionSetting.PermissionEnum.MESSAGE_SEND);

      // When
      ModifyPermissionSetting setting =
          new ModifyPermissionSetting(123L, "role", allowToAdd, Set.of(), denyToAdd, Set.of());

      // Then
      assertThat(setting.targetId()).isEqualTo(123L);
      assertThat(setting.targetType()).isEqualTo("role");
      assertThat(setting.allowToAdd())
          .containsExactly(ModifyPermissionSetting.PermissionEnum.VIEW_CHANNEL);
      assertThat(setting.denyToAdd())
          .containsExactly(ModifyPermissionSetting.PermissionEnum.MESSAGE_SEND);
    }

    @Test
    @DisplayName("should default targetType to role")
    void shouldDefaultTargetTypeToRole() {
      // When
      ModifyPermissionSetting setting =
          new ModifyPermissionSetting(123L, null, Set.of(), Set.of(), Set.of(), Set.of());

      // Then
      assertThat(setting.targetType()).isEqualTo("role");
    }

    @Test
    @DisplayName("should default empty sets when null")
    void shouldDefaultEmptySetsWhenNull() {
      // When
      ModifyPermissionSetting setting =
          new ModifyPermissionSetting(123L, "member", null, null, null, null);

      // Then
      assertThat(setting.allowToAdd()).isEmpty();
      assertThat(setting.allowToRemove()).isEmpty();
      assertThat(setting.denyToAdd()).isEmpty();
      assertThat(setting.denyToRemove()).isEmpty();
    }

    @Test
    @DisplayName("should return true when setting is valid with non-empty allowToAdd")
    void shouldReturnTrueWhenValidWithAllowToAdd() {
      // Given
      ModifyPermissionSetting setting =
          new ModifyPermissionSetting(
              123L,
              "role",
              Set.of(ModifyPermissionSetting.PermissionEnum.VIEW_CHANNEL),
              Set.of(),
              Set.of(),
              Set.of());

      // When
      boolean result = setting.isValid();

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return true when setting is valid with non-empty denyToAdd")
    void shouldReturnTrueWhenValidWithDenyToAdd() {
      // Given
      ModifyPermissionSetting setting =
          new ModifyPermissionSetting(
              123L,
              "role",
              Set.of(),
              Set.of(),
              Set.of(ModifyPermissionSetting.PermissionEnum.MESSAGE_SEND),
              Set.of());

      // When
      boolean result = setting.isValid();

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return false when targetId is zero")
    void shouldReturnFalseWhenTargetIdIsZero() {
      // Given
      ModifyPermissionSetting setting =
          new ModifyPermissionSetting(
              0L,
              "role",
              Set.of(ModifyPermissionSetting.PermissionEnum.VIEW_CHANNEL),
              Set.of(),
              Set.of(),
              Set.of());

      // When
      boolean result = setting.isValid();

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should return false when all permission sets are empty")
    void shouldReturnFalseWhenAllPermissionSetsAreEmpty() {
      // Given
      ModifyPermissionSetting setting =
          new ModifyPermissionSetting(123L, "role", Set.of(), Set.of(), Set.of(), Set.of());

      // When
      boolean result = setting.isValid();

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should have all permission enum values")
    void shouldHaveAllPermissionEnumValues() {
      // Then
      ModifyPermissionSetting.PermissionEnum[] values =
          ModifyPermissionSetting.PermissionEnum.values();
      assertThat(values).hasSize(16);
      assertThat(values)
          .contains(
              ModifyPermissionSetting.PermissionEnum.ADMINISTRATOR,
              ModifyPermissionSetting.PermissionEnum.VIEW_CHANNEL,
              ModifyPermissionSetting.PermissionEnum.MESSAGE_SEND);
    }
  }
}
