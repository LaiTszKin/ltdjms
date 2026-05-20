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
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { type EscortOptionCatalogRepository } from '@ltdjms/dispatch';
import { AdminPanelModalFactory } from '../views/AdminPanelModalFactory.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for escort catalog interactions (admin_escortcatalog_*).
 * Supports CRUD operations on the global escort option catalog.
 *
 * NOTE: The underlying EscortOptionCatalogRepository is currently a stub
 * that returns empty arrays. Full CRUD persistence requires extending the
 * repository interface with create/update/delete methods.
 */
export class EscortCatalogHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_escortcatalog';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly catalogRepository: EscortOptionCatalogRepository,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.ESCORT_CATALOG);

    const fullCustomId = interaction.getCustomId();

    // Handle modal submits
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
        // NOTE: The catalog repository does not support delete yet.
        // Show a message indicating the limitation.
        const embed = new EmbedBuilder()
          .setTitle(ZhTwStrings.escortCatalogTitle)
          .setDescription('目錄項目的刪除操作需要擴充 EscortOptionCatalogRepository 介面後方可使用')
          .setColor(Colors.WARNING);
        await interaction.editEmbed(embed);
      }
      return;
    }

    // Show create modal
    if (fullCustomId === 'admin_escortcatalog_create') {
      await this.showCreateModal(interaction);
      return;
    }

    // Show edit modal
    if (fullCustomId.startsWith('admin_escortcatalog_edit_')) {
      const entryCode = fullCustomId.replace('admin_escortcatalog_edit_', '');
      if (entryCode) {
        await this.showEditModal(interaction, entryCode);
      }
      return;
    }

    // Default: show catalog list
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
    // NOTE: EscortOptionCatalogRepository does not have a save/create method yet.
    // Display a message indicating this limitation.
    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.escortCatalogTitle)
      .setDescription('目錄項目的建立操作需要擴充 EscortOptionCatalogRepository 介面後方可使用')
      .setColor(Colors.WARNING);
    await interaction.editEmbed(embed);
  }

  private async handleEditSave(
    interaction: DiscordInteraction,
    _entryCode: string,
  ): Promise<void> {
    // NOTE: EscortOptionCatalogRepository does not have an update method yet.
    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.escortCatalogTitle)
      .setDescription('目錄項目的編輯操作需要擴充 EscortOptionCatalogRepository 介面後方可使用')
      .setColor(Colors.WARNING);
    await interaction.editEmbed(embed);
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
