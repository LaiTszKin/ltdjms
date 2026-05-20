import {
  type DiscordInteraction,
  type DiscordContext,
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
import { DispatchManagementFacade } from '../../../facades/DispatchManagementFacade.js';
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
    private readonly facade: DispatchManagementFacade,
    private readonly modalFactory: AdminPanelModalFactory,
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

    // Handle back to catalog list
    if (fullCustomId === 'admin_escortcatalog_back') {
      this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.ESCORT_CATALOG);
      await this.showCatalog(interaction);
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
    const entryResult = await this.facade.findCatalogEntry(entryCode);
    const entry = entryResult.isOk() ? entryResult.getValue() : null;

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

    const existingEntry = await this.facade.findCatalogEntry(code);
    if (existingEntry.isOk() && existingEntry.getValue()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(`代碼「${code}」已存在`)
        .setColor(Colors.WARNING);
      await interaction.editEmbed(embed);
      return;
    }

    const guildId = interaction.getGuildId();
    const result = await this.facade.createCatalogEntry(guildId, {
      code,
      type,
      level: type,
      mapScope: mapScope || code,
      target: code,
      priceTwd,
    });

    if (result.isOk()) {
      const entry = result.getValue();
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(ZhTwStrings.escortCatalogCreated.replace('{name}', `${entry.type} - ${entry.target}`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
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

    const guildId = interaction.getGuildId();
    const result = await this.facade.updateCatalogEntry(guildId, entryCode, {
      mapScope: mapScope || undefined,
      priceTwd,
      type,
    });

    if (result.isOk()) {
      const updated = result.getValue();
      if (!updated) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.escortCatalogTitle)
          .setDescription('找不到該目錄項目')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(ZhTwStrings.escortCatalogUpdated.replace('{name}', `${updated.type} - ${updated.target}`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async showDeleteConfirmation(
    interaction: DiscordInteraction,
    entryCode: string,
  ): Promise<void> {
    const entryResult = await this.facade.findCatalogEntry(entryCode);
    const existing = entryResult.isOk() ? entryResult.getValue() : null;
    const name = existing ? `${existing.type} - ${existing.target}` : entryCode;

    const refCountResult = await this.facade.checkCatalogRefCount(entryCode);
    const refCount = refCountResult.isOk() ? refCountResult.getValue() : 0;
    if (refCount > 0) {
      // Query guild names for the referencing guilds
      const guildIdsResult = await this.facade.findCatalogRefGuildIds(entryCode);
      const guildList = guildIdsResult.isOk() && guildIdsResult.getValue().length > 0
        ? guildIdsResult.getValue().map((id) => `Guild ${id}`).join('\n')
        : `${refCount} 個 guild`;

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(
          ZhTwStrings.escortCatalogDeleteBlocked
            .replace('{name}', name)
            .replace('{guilds}', guildList),
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
    const result = await this.facade.deleteCatalogEntry(guildId, entryCode);

    if (result.isOk()) {
      if (!result.getValue()) {
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.escortCatalogTitle)
          .setDescription('找不到該目錄項目')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
        return;
      }

      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.escortCatalogTitle)
        .setDescription(ZhTwStrings.escortCatalogDeleted.replace('{name}', entryCode))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async showCatalog(interaction: DiscordInteraction): Promise<void> {
    const result = await this.facade.listCatalog();
    if (result.isOk()) {
      const entries = result.getValue();
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

      // Build action row with "add item" button
      const rows: ActionRowBuilder<ButtonBuilder>[] = [];

      const addBtn = new ButtonBuilder()
        .setCustomId('admin_escortcatalog_create')
        .setLabel(ZhTwStrings.escortCatalogCreateBtn)
        .setStyle(ButtonStyle.Success);

      rows.push(new ActionRowBuilder<ButtonBuilder>().addComponents(addBtn));

      // Per-item edit/delete buttons
      if (entries.length > 0) {
        const entryButtons = entries.flatMap((entry) => {
          const editBtn = new ButtonBuilder()
            .setCustomId('admin_escortcatalog_edit_' + entry.code)
            .setLabel(`${ZhTwStrings.escortCatalogEditBtn} ${entry.code}`)
            .setStyle(ButtonStyle.Primary);
          const deleteBtn = new ButtonBuilder()
            .setCustomId('admin_escortcatalog_delete_' + entry.code)
            .setLabel(`${ZhTwStrings.escortCatalogDeleteBtn} ${entry.code}`)
            .setStyle(ButtonStyle.Danger);
          return [editBtn, deleteBtn];
        });

        // Group into rows of 2 (one edit + delete pair per row)
        for (let i = 0; i < entryButtons.length; i += 2) {
          const chunk = entryButtons.slice(i, i + 2);
          rows.push(new ActionRowBuilder<ButtonBuilder>().addComponents(chunk));
        }
      }

      const raw = interaction.getHook() as {
        editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
      };
      await raw.editReply({ embeds: [embed], components: rows });
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }
}
