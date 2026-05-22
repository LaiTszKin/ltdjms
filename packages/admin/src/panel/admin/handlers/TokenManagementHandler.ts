import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  UserSelectMenuBuilder,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { GameTokenManagementFacade } from '@ltdjms/games';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { AdminPanelModalFactory } from '../views/AdminPanelModalFactory.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for token management interactions (admin_token_*).
 * Supports select member, view tokens, add/deduct/set via modal.
 */
export class TokenManagementHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_token';

  constructor(
    private readonly facade: GameTokenManagementFacade,
    private readonly modalFactory: AdminPanelModalFactory,
    sessionManager: AdminPanelSessionManager,
    errorHandler: BotErrorHandler,
  ) {
    super(sessionManager, errorHandler);
  }

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
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

    const fullCustomId = interaction.getCustomId();

    // Sub-action: show modal for add/deduct/set — must NOT defer before showModal
    if (fullCustomId.startsWith('admin_token_modal_')) {
      this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.TOKEN);
      const mode = fullCustomId.replace('admin_token_modal_', '') as 'add' | 'deduct' | 'set';
      await this.showAdjustModal(interaction, mode);
      return;
    }

    await this.ensureDeferred(interaction);

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.TOKEN);

    // Modal submit handling
    if (
      fullCustomId === 'admin_token_add' ||
      fullCustomId === 'admin_token_deduct' ||
      fullCustomId === 'admin_token_set'
    ) {
      await this.handleModalSubmit(interaction, guildId, userId, fullCustomId);
      return;
    }

    // Handle member selection from user select menu
    if (fullCustomId === 'admin_token_select_member') {
      const selectedValues = interaction.getSelectedValues();
      const selectedUserId = selectedValues[0];
      if (selectedUserId) {
        this.sessionManager.setContext(guildId, userId, 'selectedUserId', selectedUserId);
        await this.showTokenView(interaction, guildId, selectedUserId);
      } else {
        await this.showMemberSelect(interaction);
      }
      return;
    }

    // Default: check if a member is already selected, show member selection or token view
    const selectedUserId = this.sessionManager.getContext(guildId, userId, 'selectedUserId');
    if (!selectedUserId) {
      await this.showMemberSelect(interaction);
    } else {
      await this.showTokenView(interaction, guildId, selectedUserId);
    }
  }

  private async showMemberSelect(interaction: DiscordInteraction): Promise<void> {
    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.tokenTitle)
      .setDescription(ZhTwStrings.tokenSelectMember)
      .setColor(Colors.PRIMARY);

    // Use UserSelectMenu for real member selection
    const select = new UserSelectMenuBuilder()
      .setCustomId('admin_token_select_member')
      .setPlaceholder('請選擇成員');

    const row = new ActionRowBuilder<UserSelectMenuBuilder>().addComponents(select);
    await interaction.editWithComponents(embed, [row]);
  }

  private async showTokenView(
    interaction: DiscordInteraction,
    guildId: string,
    targetUserId: string,
  ): Promise<void> {
    const result = await this.facade.getTokens(guildId, targetUserId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription(ZhTwStrings.tokenDisplay.replace('{tokens}', String(result.getValue())))
        .setColor(Colors.PRIMARY);

      const addBtn = new ButtonBuilder()
        .setCustomId('admin_token_modal_add')
        .setLabel(ZhTwStrings.balanceAdjustAdd)
        .setStyle(ButtonStyle.Success);
      const deductBtn = new ButtonBuilder()
        .setCustomId('admin_token_modal_deduct')
        .setLabel(ZhTwStrings.balanceAdjustDeduct)
        .setStyle(ButtonStyle.Danger);
      const setBtn = new ButtonBuilder()
        .setCustomId('admin_token_modal_set')
        .setLabel(ZhTwStrings.balanceAdjustSet)
        .setStyle(ButtonStyle.Primary);

      const row = new ActionRowBuilder<ButtonBuilder>().addComponents(addBtn, deductBtn, setBtn);
      await interaction.editWithComponents(embed, [row]);
    } else {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription('無法取得該成員的代幣資訊')
        .setColor(Colors.PRIMARY);
      await interaction.editWithComponents(embed, []);
    }
  }

  private async showAdjustModal(
    interaction: DiscordInteraction,
    mode: 'add' | 'deduct' | 'set',
  ): Promise<void> {
    const modalData = this.modalFactory.buildTokenAdjustModal(mode);

    const modal = new ModalBuilder().setCustomId('admin_token_' + mode).setTitle(modalData.title);

    for (const field of modalData.fields) {
      const input = new TextInputBuilder()
        .setCustomId(field.label)
        .setLabel(field.label)
        .setStyle(TextInputStyle.Short)
        .setMinLength(field.minLength)
        .setMaxLength(field.maxLength)
        .setRequired(field.required);
      if ('placeholder' in field && field.placeholder) {
        input.setPlaceholder(field.placeholder);
      }
      modal.addComponents(new ActionRowBuilder<TextInputBuilder>().addComponents(input));
    }

    await interaction.showModal(modal);
  }

  private async handleModalSubmit(
    interaction: DiscordInteraction,
    guildId: string,
    actorId: string,
    customId: string,
  ): Promise<void> {
    const mode = customId.replace('admin_token_', '') as 'add' | 'deduct' | 'set';

    const selectedUserId = this.sessionManager.getContext(guildId, actorId, 'selectedUserId');
    if (!selectedUserId) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription('請先選擇成員')
        .setColor(Colors.PRIMARY);
      await interaction.editEmbed(embed);
      return;
    }

    const amountStr = interaction.getTextInputValue('數量');
    const reason = interaction.getTextInputValue('原因');
    const amount = parseInt(amountStr, 10);

    if (isNaN(amount) || (mode === 'set' ? amount < 0 : amount <= 0)) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription('請輸入有效的正整數數量')
        .setColor(Colors.PRIMARY);
      await interaction.editEmbed(embed);
      return;
    }

    let result;
    if (mode === 'add') {
      result = await this.facade.adjustTokens(guildId, selectedUserId, amount, reason, actorId);
    } else if (mode === 'deduct') {
      result = await this.facade.adjustTokens(guildId, selectedUserId, -amount, reason, actorId);
    } else {
      result = await this.facade.setTokens(guildId, selectedUserId, amount, reason, actorId);
    }

    if (result.isOk()) {
      const adjustResult = result.getValue();
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription(
          ZhTwStrings.tokenSuccessAdjust
            .replace('{before}', String(adjustResult.previousTokens))
            .replace('{after}', String(adjustResult.newTokens)),
        )
        .setColor(Colors.PRIMARY);
      await interaction.editEmbed(embed);
    } else {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription(ZhTwStrings.tokenErrorPrefix + result.getError().message)
        .setColor(Colors.PRIMARY);
      await interaction.editEmbed(embed);
    }
  }
}
