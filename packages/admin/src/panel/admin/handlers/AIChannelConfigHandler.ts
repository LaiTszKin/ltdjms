import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  ChannelType,
  ChannelSelectMenuBuilder,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { AIConfigManagementFacade } from '../../../facades/AIConfigManagementFacade.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for AI channel config interactions (admin_aichannel_*).
 * Supports add/remove channels and categories from the AI allowlist.
 */
export class AIChannelConfigHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_aichannel';

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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.AI_CHANNEL);

    const fullCustomId = interaction.getCustomId();

    // Handle channel select menu results
    if (fullCustomId === 'admin_aichannel_add_channel_select') {
      await this.handleAddChannel(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aichannel_remove_channel_select') {
      await this.handleRemoveChannel(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aichannel_add_category_confirm') {
      await this.handleAddCategoryFromRaw(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aichannel_remove_category_confirm') {
      await this.handleRemoveCategoryFromRaw(interaction, guildId);
      return;
    }

    // Branch on sub-action — show appropriate select menu
    if (fullCustomId === 'admin_aichannel_add_channel') {
      await this.showChannelSelect(interaction, guildId, 'add');
      return;
    }
    if (fullCustomId === 'admin_aichannel_remove_channel') {
      await this.showChannelSelect(interaction, guildId, 'remove');
      return;
    }
    if (fullCustomId === 'admin_aichannel_add_category') {
      await this.showCategoryConfig(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_aichannel_remove_category') {
      await this.showCategoryConfig(interaction, guildId);
      return;
    }

    // Default: show channel config overview
    await this.showChannelConfig(interaction, guildId);
  }

  private async showChannelSelect(
    interaction: DiscordInteraction,
    _guildId: string,
    action: 'add' | 'remove',
  ): Promise<void> {
    const customId = action === 'add'
      ? 'admin_aichannel_add_channel_select'
      : 'admin_aichannel_remove_channel_select';

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiChannelTitle)
      .setDescription(`請選擇要${action === 'add' ? '新增' : '移除'}的頻道`)
      .setColor(Colors.PRIMARY);

    const select = new ChannelSelectMenuBuilder()
      .setCustomId(customId)
      .setPlaceholder('請選擇頻道')
      .setChannelTypes(ChannelType.GuildText);

    const row = new ActionRowBuilder<ChannelSelectMenuBuilder>().addComponents(select);
    await interaction.editWithComponents(embed, [row]);
  }

  private async handleAddChannel(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedValues = interaction.getSelectedValues();
    if (!selectedValues || selectedValues.length === 0) {
      await this.showChannelConfig(interaction, guildId);
      return;
    }

    const channelId = selectedValues[0];
    const channelName = interaction.getChannelName(channelId) ?? channelId;
    const result = await this.facade.addAllowedChannel(guildId, channelId, channelName);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.aiChannelTitle)
        .setDescription(ZhTwStrings.aiChannelAdded.replace('{channel}', `<#${channelId}>`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async handleRemoveChannel(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedValues = interaction.getSelectedValues();
    if (!selectedValues || selectedValues.length === 0) {
      await this.showChannelConfig(interaction, guildId);
      return;
    }

    const channelId = selectedValues[0];
    const result = await this.facade.removeAllowedChannel(guildId, channelId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.aiChannelTitle)
        .setDescription(ZhTwStrings.aiChannelRemoved.replace('{channel}', `<#${channelId}>`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async handleAddCategoryFromRaw(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedValues = interaction.getSelectedValues();
    const selectedCategory = String(selectedValues[0] ?? '');
    if (!selectedCategory) {
      await this.showChannelConfig(interaction, guildId);
      return;
    }

    const categoryName = interaction.getChannelName(selectedCategory) ?? selectedCategory;
    const result = await this.facade.addAllowedCategory(guildId, selectedCategory, categoryName);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.aiChannelTitle)
        .setDescription(ZhTwStrings.aiCategoryAdded.replace('{category}', selectedCategory))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async handleRemoveCategoryFromRaw(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedValues = interaction.getSelectedValues();
    const selectedCategory = String(selectedValues[0] ?? '');
    if (!selectedCategory) {
      await this.showChannelConfig(interaction, guildId);
      return;
    }

    const result = await this.facade.removeAllowedCategory(guildId, selectedCategory);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.aiChannelTitle)
        .setDescription(ZhTwStrings.aiCategoryRemoved.replace('{category}', selectedCategory))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async showCategoryConfig(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    // For categories, use the raw interaction to show a simple text-input approach
    // since Discord does not provide a dedicated category select menu for interactions.
    // We show the current channel config and prompt the admin to use the category ID.
    const [channelsResult, categoriesResult] = await Promise.all([
      this.facade.getAllowedChannels(guildId),
      this.facade.getAllowedCategories(guildId),
    ]);

    const channelList = channelsResult.isOk() && channelsResult.getValue().length > 0
      ? channelsResult.getValue().map((c) => `<#${c.channelId}>`).join('\n')
      : '無';
    const categoryList = categoriesResult.isOk() && categoriesResult.getValue().length > 0
      ? categoriesResult.getValue().map((c) => c.categoryName).join('\n')
      : '無';

    const description = (channelsResult.isOk() && channelsResult.getValue().length === 0 &&
      categoriesResult.isOk() && categoriesResult.getValue().length === 0)
      ? ZhTwStrings.aiChannelEmpty
      : ZhTwStrings.aiChannelList
          .replace('{channels}', channelList)
          .replace('{categories}', categoryList);

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiChannelTitle)
      .setDescription(description)
      .setColor(Colors.PRIMARY);
    await interaction.editEmbed(embed);
  }

  private async showChannelConfig(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const [channelsResult, categoriesResult] = await Promise.all([
      this.facade.getAllowedChannels(guildId),
      this.facade.getAllowedCategories(guildId),
    ]);

    const channelList = channelsResult.isOk() && channelsResult.getValue().length > 0
      ? channelsResult.getValue().map((c) => `<#${c.channelId}>`).join('\n')
      : '無';
    const categoryList = categoriesResult.isOk() && categoriesResult.getValue().length > 0
      ? categoriesResult.getValue().map((c) => c.categoryName).join('\n')
      : '無';

    const description = (channelsResult.isOk() && channelsResult.getValue().length === 0 &&
      categoriesResult.isOk() && categoriesResult.getValue().length === 0)
      ? ZhTwStrings.aiChannelEmpty
      : ZhTwStrings.aiChannelList
          .replace('{channels}', channelList)
          .replace('{categories}', categoryList);

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.aiChannelTitle)
      .setDescription(description)
      .setColor(Colors.PRIMARY);
    await interaction.editEmbed(embed);
  }
}
