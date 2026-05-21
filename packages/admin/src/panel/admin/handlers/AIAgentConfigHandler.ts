import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  ChannelType,
  ChannelSelectMenuBuilder,
  StringSelectMenuBuilder,
  StringSelectMenuOptionBuilder,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { AIConfigManagementFacade } from '../../../facades/AIConfigManagementFacade.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for AI agent config interactions (admin_aiagent_*).
 * Supports enable/disable/remove agent mode on channels.
 */
export class AIAgentConfigHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_aiagent';

  constructor(
    private readonly facade: AIConfigManagementFacade,
    sessionManager: AdminPanelSessionManager,
    errorHandler: BotErrorHandler,
  ) {
    super(sessionManager, errorHandler);
  }

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Permission check
    if (!this.checkAdminPermission(interaction)) {
      await interaction.reply(ZhTwStrings.permissionAdminRequired);
      return;
    }

    const session = this.getSession(interaction);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await this.ensureDeferred(interaction);

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.AI_AGENT);

    const fullCustomId = interaction.getCustomId();

    // Handle channel select menu results
    if (fullCustomId === 'admin_aiagent_enable_select') {
      await this.handleChannelSelected(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aiagent_enable_mode_select') {
      await this.handleEnableAgentWithMode(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aiagent_disable_select') {
      await this.handleDisableAgent(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aiagent_remove_select') {
      await this.handleRemoveAgent(interaction, guildId);
      return;
    }

    // Branch on sub-action — show channel select
    if (fullCustomId === 'admin_aiagent_enable') {
      await this.showChannelSelect(interaction, 'enable');
      return;
    }
    if (fullCustomId === 'admin_aiagent_disable') {
      await this.showChannelSelect(interaction, 'disable');
      return;
    }
    if (fullCustomId === 'admin_aiagent_remove') {
      await this.showChannelSelect(interaction, 'remove');
      return;
    }

    // Default: show agent config overview
    await this.showAgentConfig(interaction, guildId);
  }

  private async showChannelSelect(
    interaction: DiscordInteraction,
    action: 'enable' | 'disable' | 'remove',
  ): Promise<void> {
    const customIdMap: Record<string, string> = {
      enable: 'admin_aiagent_enable_select',
      disable: 'admin_aiagent_disable_select',
      remove: 'admin_aiagent_remove_select',
    };
    const descMap: Record<string, string> = {
      enable: ZhTwStrings.aiAgentSelectChannel,
      disable: ZhTwStrings.aiAgentSelectDisableChannel,
      remove: ZhTwStrings.aiAgentSelectRemoveChannel,
    };

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiAgentTitle)
      .setDescription(descMap[action])
      .setColor(Colors.PRIMARY);

    const select = new ChannelSelectMenuBuilder()
      .setCustomId(customIdMap[action])
      .setPlaceholder('請選擇頻道')
      .setChannelTypes(ChannelType.GuildText);

    const row = new ActionRowBuilder<ChannelSelectMenuBuilder>().addComponents(select);
    await interaction.editWithComponents(embed, [row]);
  }

  private async handleChannelSelected(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedValues = interaction.getSelectedValues();
    if (!selectedValues || selectedValues.length === 0) {
      await this.showAgentConfig(interaction, guildId);
      return;
    }

    const channelId = selectedValues[0];
    // Store channelId in session context for the next step
    this.sessionManager.setContext(guildId, interaction.getUserId(), 'agent_channel', channelId);

    // Show mode selection menu
    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiAgentTitle)
      .setDescription('請選擇 Agent 模式')
      .setColor(Colors.PRIMARY);

    const modeSelect = new StringSelectMenuBuilder()
      .setCustomId('admin_aiagent_enable_mode_select')
      .setPlaceholder(ZhTwStrings.aiAgentModeLabel)
      .addOptions(
        new StringSelectMenuOptionBuilder()
          .setLabel('Chat')
          .setDescription('純聊天模式，不包含 Agent 工具')
          .setValue('chat'),
        new StringSelectMenuOptionBuilder()
          .setLabel('Agent')
          .setDescription('含工具執行的完整 Agent 模式')
          .setValue('agent'),
        new StringSelectMenuOptionBuilder()
          .setLabel('Hybrid')
          .setDescription('聊天 + Agent 混合模式')
          .setValue('hybrid'),
      );

    const row = new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(modeSelect);
    await interaction.editWithComponents(embed, [row]);
  }

  private async handleEnableAgentWithMode(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const channelId = this.sessionManager.getContext(guildId, interaction.getUserId(), 'agent_channel');
    if (!channelId) {
      await this.showAgentConfig(interaction, guildId);
      return;
    }

    const result = await this.facade.enableAgent(guildId, channelId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.aiAgentTitle)
        .setDescription(ZhTwStrings.aiAgentEnabled.replace('{channel}', `<#${channelId}>`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async handleDisableAgent(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedValues = interaction.getSelectedValues();
    if (!selectedValues || selectedValues.length === 0) {
      await this.showAgentConfig(interaction, guildId);
      return;
    }

    const channelId = selectedValues[0];
    const result = await this.facade.disableAgent(guildId, channelId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.aiAgentTitle)
        .setDescription(ZhTwStrings.aiAgentDisabled.replace('{channel}', `<#${channelId}>`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async handleRemoveAgent(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedValues = interaction.getSelectedValues();
    if (!selectedValues || selectedValues.length === 0) {
      await this.showAgentConfig(interaction, guildId);
      return;
    }

    const channelId = selectedValues[0];
    const result = await this.facade.removeAgentConfig(guildId, channelId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.aiAgentTitle)
        .setDescription(ZhTwStrings.aiAgentRemoved.replace('{channel}', `<#${channelId}>`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async showAgentConfig(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const result = await this.facade.getAgentConfigs(guildId);

    let description: string;
    if (result.isOk() && result.getValue().length > 0) {
      const channelList = result.getValue().map((ch) => {
        // TODO(P2-32): 當 AIAgentChannelConfigService 支援 mode 與啟用時間查詢時，
        // 改為顯示 <#ch> (mode, enabledAt) 格式。目前僅顯示 channel mention。
        return `<#${ch}> (mode: default)`;
      }).join('\n');
      description = ZhTwStrings.aiAgentList.replace('{channels}', channelList);
    } else {
      description = ZhTwStrings.aiAgentEmpty;
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiAgentTitle)
      .setDescription(description)
      .setColor(Colors.PRIMARY);
    await interaction.editEmbed(embed);
  }
}
