import {
  type DiscordInteraction,
  type DiscordContext,
  type DomainEventPublisher,
  type EscortCatalogChangedEvent,
  OperationType,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import {
  type EscortOptionCatalogRepository,
  type EscortOptionPriceRepo,
} from '@ltdjms/dispatch';
import { AdminPanelModalFactory } from '../views/AdminPanelModalFactory.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for escort catalog interactions (admin_escortcatalog_*).
 * Supports CRUD operations on the global escort option catalog.
 */
export class EscortCatalogHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_escortcatalog';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly catalogRepository: EscortOptionCatalogRepository,
    private readonly modalFactory: AdminPanelModalFactory,
    private readonly optionPriceRepo: EscortOptionPriceRepo,
    private readonly eventPublisher: DomainEventPublisher,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.ESCORT_CATALOG);

    const fullCustomId = interaction.getCustomId();

    if (fullCustomId === 'admin_escortcatalog_create_save') {
      await this.handleCreateSave(interaction);
      return;
    }
    if (fullCustomId.startsWith('admin_escortcatalog_edit_save_')) {
      const entryCode = fullCustomId.replace('admin_escortcatalog_edit_save_', '');
      if (entryCode) {
        await this.handleEditSave(interaction, entryCode);
      }
      return;
    }

    if (fullCustomId.startsWith('admin_escortcatalog_delete_')) {
      const entryCode = fullCustomId.replace('admin_escortcatalog_delete_', '');
      if (entryCode) {
        await this.showDeleteConfirmation(interaction, entryCode);
      }
      return;
    }

    if (fullCustomId.startsWith('admin_escortcatalog_confirm_delete_')) {
      const entryCode = fullCustomId.replace('admin_escortcatalog_confirm_delete_', '');
      if (entryCode) {
        await this.handleDelete(interaction, guildId, entryCode);
      }
      return;
    }

    if (fullCustomId === 'admin_escortcatalog_create') {
      await this.showCreateModal(interaction);
      return;
    }

    if (fullCustomId.startsWith('admin_escortcatalog_edit_')) {
      const entryCode = fullCustomId.replace('admin_escortcatalog_edit_', '');
      if (entryCode) {
        await this.showEditModal(interaction, entryCode);
      }
      return;
    }

    await this.showCatalog(interaction);
  }

  private async showCreateModal(interaction: DiscordInteraction): Promise<void> {
    const modalData = this.modalFactory.buildEscortCatalogModal(null);

    const modal = new ModalBuilder()
      .setCustomId('admin_escortcatalog_create_save')
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
      if ('value' in field && field.value) {
        input.setValue(field.value as string);
      }
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(input),
      );
    }

    const raw = interaction.getHook() as { showModal: (m: ModalBuilder) => Promise<void> };
    await raw.showModal(modal);
  }

  private async showEditModal(
    interaction: DiscordInteraction,
    entryCode: string,
  ): Promise<void> {
    const entry = await this.catalogRepository.findByCode(entryCode);

    const modalData = this.modalFactory.buildEscortCatalogModal(entry ?? { code: entryCode });

    const modal = new ModalBuilder()
      .setCustomId('admin_escortcatalog_edit_save_' + entryCode)
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
      if ('value' in field && field.value) {
        input.setValue(field.value as string);
      }
      modal.addComponents(
        new ActionRowBuilder<TextInputBuilder>().addComponents(input),
      );
    }

    const raw = interaction.getHook() as { showModal: (m: ModalBuilder) => Promise<void> };
    await raw.showModal(modal);
  }

  private async handleCreateSave(interaction: DiscordInteraction): Promise<void> {
    const raw = interaction.getHook() as {
      fields: { getTextInputValue: (id: string) => string };
    };

    const code = raw.fields.getTextInputValue(ZhTwStrings.escortCatalogModalName).trim();
    const mapScope = raw.fields.getTextInputValue(ZhTwStrings.escortCatalogModalDesc).trim();
    const priceStr = raw.fields.getTextInputValue(ZhTwStrings.escortCatalogModalPrice).trim();
    const type = raw.fields.getTextInputValue(ZhTwStrings.escortCatalogModalCategory).trim();

    if (!code || !type || !priceStr) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription('名稱、類別與基礎價格為必填欄位')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const priceTwd = parseInt(priceStr, 10);
    if (isNaN(priceTwd) || priceTwd <= 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription('請輸入有效的正整數價格')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const exists = await this.catalogRepository.existsByCode(code);
    if (exists) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(`代碼「${code}」已存在`)
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    try {
      const guildId = interaction.getGuildId();
      const entry = await this.catalogRepository.create({
        code,
        type,
        level: type,
        mapScope: mapScope || code,
        target: code,
        priceTwd,
      });

      this.eventPublisher.publish({
        eventType: 'escort_catalog_changed',
        guildId,
        entryCode: code,
        operationType: OperationType.CREATED,
      } as EscortCatalogChangedEvent);

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(ZhTwStrings.escortCatalogCreated.replace('{name}', `${entry.type} - ${entry.target}`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async handleEditSave(
    interaction: DiscordInteraction,
    entryCode: string,
  ): Promise<void> {
    const raw = interaction.getHook() as {
      fields: { getTextInputValue: (id: string) => string };
    };

    const mapScope = raw.fields.getTextInputValue(ZhTwStrings.escortCatalogModalDesc).trim();
    const priceStr = raw.fields.getTextInputValue(ZhTwStrings.escortCatalogModalPrice).trim();
    const type = raw.fields.getTextInputValue(ZhTwStrings.escortCatalogModalCategory).trim();

    if (!type || !priceStr) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription('類別與基礎價格為必填欄位')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const priceTwd = parseInt(priceStr, 10);
    if (isNaN(priceTwd) || priceTwd <= 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription('請輸入有效的正整數價格')
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    try {
      const guildId = interaction.getGuildId();
      const updated = await this.catalogRepository.update(entryCode, {
        mapScope: mapScope || undefined,
        priceTwd,
        type,
      });

      if (!updated) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.escortCatalogTitle)
          .setDescription('找不到該目錄項目')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      this.eventPublisher.publish({
        eventType: 'escort_catalog_changed',
        guildId,
        entryCode,
        operationType: OperationType.UPDATED,
      } as EscortCatalogChangedEvent);

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(ZhTwStrings.escortCatalogUpdated.replace('{name}', `${updated.type} - ${updated.target}`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async showDeleteConfirmation(
    interaction: DiscordInteraction,
    entryCode: string,
  ): Promise<void> {
    const entry = await this.catalogRepository.findByCode(entryCode);
    const name = entry ? `${entry.type} - ${entry.target}` : entryCode;

    const refCount = await this.optionPriceRepo.countByOptionCode(entryCode);
    if (refCount > 0) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(
          ZhTwStrings.escortCatalogDeleteBlocked
            .replace('{name}', name)
            .replace('{guilds}', `${refCount} 個 guild`),
        )
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.escortCatalogTitle)
      .setDescription(
        ZhTwStrings.escortCatalogConfirmDelete.replace('{name}', name),
      )
      .setColor(Colors.WARNING);

    const confirmBtn = new ButtonBuilder()
      .setCustomId('admin_escortcatalog_confirm_delete_' + entryCode)
      .setLabel('確認刪除')
      .setStyle(ButtonStyle.Danger);

    const cancelBtn = new ButtonBuilder()
      .setCustomId('admin_escortcatalog_back')
      .setLabel('取消')
      .setStyle(ButtonStyle.Secondary);

    const row = new ActionRowBuilder<ButtonBuilder>().addComponents(confirmBtn, cancelBtn);

    const raw = interaction.getHook() as {
      editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
    };
    await raw.editReply({ embeds: [embed], components: [row] });
  }

  private async handleDelete(
    interaction: DiscordInteraction,
    guildId: string,
    entryCode: string,
  ): Promise<void> {
    try {
      const deleted = await this.catalogRepository.delete(entryCode);

      if (!deleted) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.escortCatalogTitle)
          .setDescription('找不到該目錄項目')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      this.eventPublisher.publish({
        eventType: 'escort_catalog_changed',
        guildId,
        entryCode,
        operationType: OperationType.DELETED,
      } as EscortCatalogChangedEvent);

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(ZhTwStrings.escortCatalogDeleted.replace('{name}', entryCode))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }

  private async showCatalog(interaction: DiscordInteraction): Promise<void> {
    try {
      const entries = await this.catalogRepository.findAll();
      let description: string;

      if (entries.length === 0) {
        description = ZhTwStrings.escortCatalogEmpty;
      } else {
        const items = entries.map((entry) =>
          ZhTwStrings.escortCatalogItem
            .replace('{name}', `${entry.type} - ${entry.target}`)
            .replace('{category}', entry.level)
            .replace('{price}', String(entry.priceTwd))
            .replace('{description}', entry.mapScope),
        );
        description = items.join('\n\n');
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(description)
        .setColor(Colors.PRIMARY);
      await interaction.editEmbed(embed);
    } catch (err) {
      await this.errorHandler.handle(err, interaction);
    }
  }
}
