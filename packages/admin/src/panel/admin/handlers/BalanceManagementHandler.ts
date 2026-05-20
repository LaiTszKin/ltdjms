import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  StringSelectMenuBuilder,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
} from 'discord.js';
import { CurrencyManagementFacade } from '../../../facades/CurrencyManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { AdminPanelModalFactory } from '../views/AdminPanelModalFactory.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';

/**
 * Handler for balance management interactions (admin_balance_*).
 * Supports select member, view balance, add/deduct/set via modal.
 */
export class BalanceManagementHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_balance';

  constructor(
    private readonly facade: CurrencyManagementFacade,
    private readonly modalFactory: AdminPanelModalFactory,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.BALANCE);

    const fullCustomId = interaction.getCustomId();

    // Modal submit handling
    if (fullCustomId === 'admin_balance_add' || fullCustomId === 'admin_balance_deduct' || fullCustomId === 'admin_balance_set') {
      await this.handleModalSubmit(interaction, guildId, userId, fullCustomId);
      return;
    }

    // Sub-action: show modal for add/deduct/set
    if (fullCustomId.startsWith('admin_balance_modal_')) {
      const mode = fullCustomId.replace('admin_balance_modal_', '') as 'add' | 'deduct' | 'set';
      await this.showAdjustModal(interaction, mode);
      return;
    }

    // Handle member selection from select menu
    if (fullCustomId === 'admin_balance_select_member') {
      const rawHook = interaction.getHook() as { values?: string[] };
      const selectedUserId = rawHook.values?.[0];
      if (selectedUserId) {
        this.sessionManager.setContext(guildId, userId, 'selectedUserId', selectedUserId);
        await this.showBalanceView(interaction, guildId, selectedUserId);
      } else {
        await this.showMemberSelect(interaction);
      }
      return;
    }

    // Default: check if a member is already selected, show member selection or balance view
    const selectedUserId = this.sessionManager.getContext(guildId, userId, 'selectedUserId');
    if (!selectedUserId) {
      await this.showMemberSelect(interaction);
    } else {
      await this.showBalanceView(interaction, guildId, selectedUserId);
    }
  }

  private async showMemberSelect(
    interaction: DiscordInteraction,
  ): Promise<void> {
    const raw = interaction.getHook() as {
      editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<StringSelectMenuBuilder>[] }) => Promise<void>;
    };

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.balanceTitle)
      .setDescription(ZhTwStrings.balanceSelectMember)
      .setColor(0x57F287);

    // Build member select placeholders
    const select = new StringSelectMenuBuilder()
      .setCustomId('admin_balance_select_member')
      .setPlaceholder('請選擇成員')
      .addOptions([{ label: '請使用下方選單', value: '_placeholder', default: true }]);

    const row = new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(select);

    await raw.editReply({ embeds: [embed], components: [row] });
  }

  private async showBalanceView(
    interaction: DiscordInteraction,
    guildId: string,
    targetUserId: string,
  ): Promise<void> {
    const result = await this.facade.getBalance(guildId, targetUserId);

    const raw = interaction.getHook() as {
      editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<import('discord.js').ButtonBuilder>[] }) => Promise<void>;
    };

    if (result.isOk()) {
      const balanceView = result.getValue();
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.balanceTitle)
        .setDescription(
          ZhTwStrings.balanceDisplay
            .replace('{balance}', String(balanceView.balance))
            .replace('{currencyIcon}', balanceView.currencyIcon),
        )
        .setColor(0x57F287);

      const addBtn = new (await import('discord.js')).ButtonBuilder()
        .setCustomId('admin_balance_modal_add')
        .setLabel(ZhTwStrings.balanceAdjustAdd)
        .setStyle(3 as any);
      const deductBtn = new (await import('discord.js')).ButtonBuilder()
        .setCustomId('admin_balance_modal_deduct')
        .setLabel(ZhTwStrings.balanceAdjustDeduct)
        .setStyle(4 as any);
      const setBtn = new (await import('discord.js')).ButtonBuilder()
        .setCustomId('admin_balance_modal_set')
        .setLabel(ZhTwStrings.balanceAdjustSet)
        .setStyle(1 as any);

      const row = new (await import('discord.js')).ActionRowBuilder<import('discord.js').ButtonBuilder>().addComponents(addBtn, deductBtn, setBtn);

      await raw.editReply({ embeds: [embed], components: [row] });
    } else {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.balanceTitle)
        .setDescription('無法取得該成員的餘額資訊')
        .setColor(0x57F287);
      await raw.editReply({ embeds: [embed], components: [] });
    }
  }

  private async showAdjustModal(
    interaction: DiscordInteraction,
    mode: 'add' | 'deduct' | 'set',
  ): Promise<void> {
    const modalData = this.modalFactory.buildBalanceAdjustModal(mode);

    const modal = new ModalBuilder()
      .setCustomId('admin_balance_' + mode)
      .setTitle(modalData.title);

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
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(input),
      );
    }

    const raw = interaction.getHook() as {
      showModal: (modal: ModalBuilder) => Promise<void>;
    };
    await raw.showModal(modal);
  }

  private async handleModalSubmit(
    interaction: DiscordInteraction,
    guildId: string,
    actorId: string,
    customId: string,
  ): Promise<void> {
    // Extract the mode from the customId
    const mode = customId.replace('admin_balance_', '') as 'add' | 'deduct' | 'set';

    // Get the selected user from session context
    const selectedUserId = this.sessionManager.getContext(guildId, actorId, 'selectedUserId');
    if (!selectedUserId) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.balanceTitle)
        .setDescription('請先選擇成員')
        .setColor(0x57F287);
      await interaction.editEmbed(embed);
      return;
    }

    // For modal submit, the interaction is a ModalSubmitInteraction.
    // We need to get field values from the raw interaction.
    const raw = interaction.getHook() as {
      fields: { getTextInputValue: (customId: string) => string };
    };

    const amountStr = raw.fields.getTextInputValue('金額');
    const reason = raw.fields.getTextInputValue('原因');
    const amount = parseInt(amountStr, 10);

    if (isNaN(amount) || amount <= 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.balanceTitle)
        .setDescription('請輸入有效的正整數金額')
        .setColor(0x57F287);
      await interaction.editEmbed(embed);
      return;
    }

    let result;
    if (mode === 'add') {
      result = await this.facade.adjustBalance(guildId, selectedUserId, amount, reason, actorId);
    } else if (mode === 'deduct') {
      result = await this.facade.deductBalance(guildId, selectedUserId, amount, reason, actorId);
    } else {
      result = await this.facade.setBalance(guildId, selectedUserId, amount, reason, actorId);
    }

    if (result.isOk()) {
      const adjustResult = result.getValue();
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.balanceTitle)
        .setDescription(
          `調整成功！\n調整前：${adjustResult.previousBalance}\n調整後：${adjustResult.newBalance}`,
        )
        .setColor(0x57F287);
      await interaction.editEmbed(embed);
    } else {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.balanceTitle)
        .setDescription('調整失敗：' + result.getError().message)
        .setColor(0x57F287);
      await interaction.editEmbed(embed);
    }
  }
}
