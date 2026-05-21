import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  UserSelectMenuBuilder,
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { DispatchManagementFacade } from '../../../facades/DispatchManagementFacade.js';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export class DispatchAfterSalesHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_dispatch';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly facade: DispatchManagementFacade,
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

    this.sessionManager.setViewState(guildId, userId, AdminPanelViewState.DISPATCH_STAFF);

    const fullCustomId = interaction.getCustomId();

    // Handle member select menu results
    if (fullCustomId === 'admin_dispatch_add_select') {
      await this.handleAddStaff(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_dispatch_remove_select') {
      await this.handleRemoveStaff(interaction, guildId);
      return;
    }

    // Branch on sub-action
    if (fullCustomId === 'admin_dispatch_add') {
      await this.showMemberSelect(interaction, 'add');
      return;
    }
    if (fullCustomId === 'admin_dispatch_remove') {
      await this.showMemberSelect(interaction, 'remove');
      return;
    }

    // Default: show staff list overview
    await this.showStaffList(interaction, guildId);
  }

  private async showMemberSelect(
    interaction: DiscordInteraction,
    action: 'add' | 'remove',
  ): Promise<void> {
    const customId = action === 'add' ? 'admin_dispatch_add_select' : 'admin_dispatch_remove_select';
    const desc = action === 'add' ? ZhTwStrings.dispatchSelectMember : ZhTwStrings.dispatchSelectRemove;
    const title = action === 'add' ? ZhTwStrings.dispatchAddBtn : ZhTwStrings.dispatchRemoveBtn;

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.dispatchTitle)
      .setDescription(desc)
      .setColor(Colors.PRIMARY);

    const select = new UserSelectMenuBuilder()
      .setCustomId(customId)
      .setPlaceholder(title);

    const row = new ActionRowBuilder<UserSelectMenuBuilder>().addComponents(select);
    await interaction.editWithComponents(embed, [row]);
  }

  private async handleAddStaff(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedIds = interaction.getSelectedValues();
    if (selectedIds.length === 0) {
      await this.showStaffList(interaction, guildId);
      return;
    }

    const staffId = selectedIds[0];
    const result = await this.facade.addStaff(guildId, staffId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.dispatchTitle)
        .setDescription(ZhTwStrings.dispatchStaffAdded.replace('{member}', `<@${staffId}>`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async handleRemoveStaff(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const selectedIds = interaction.getSelectedValues();
    if (selectedIds.length === 0) {
      await this.showStaffList(interaction, guildId);
      return;
    }

    const staffId = selectedIds[0];
    const result = await this.facade.removeStaff(guildId, staffId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.dispatchTitle)
        .setDescription(ZhTwStrings.dispatchStaffRemoved.replace('{member}', `<@${staffId}>`))
        .setColor(Colors.SUCCESS);
      await interaction.editEmbed(embed);
    } else {
      await this.errorHandler.handle(result.getError(), interaction);
    }
  }

  private async showStaffList(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const result = await this.facade.listStaff(guildId);

    let description: string;
    if (result.isOk()) {
      const staffIds = result.getValue();
      if (staffIds.size === 0) {
        description = ZhTwStrings.dispatchStaffEmpty;
      } else {
        const staffList = Array.from(staffIds).map((id) => `<@${id}>`).join('\n');
        description = ZhTwStrings.dispatchStaffList.replace('{staffs}', staffList);
      }
    } else {
      description = '售後人員資料暫時無法取得';
    }

    const embed = new EmbedBuilder()
      .setTitle(ZhTwStrings.dispatchTitle)
      .setDescription(description)
      .setColor(Colors.PRIMARY);

    const addBtn = new ButtonBuilder()
      .setCustomId('admin_dispatch_add')
      .setLabel(ZhTwStrings.dispatchAddBtn)
      .setStyle(ButtonStyle.Success);

    const removeBtn = new ButtonBuilder()
      .setCustomId('admin_dispatch_remove')
      .setLabel(ZhTwStrings.dispatchRemoveBtn)
      .setStyle(ButtonStyle.Danger);

    const row = new ActionRowBuilder<ButtonBuilder>().addComponents(addBtn, removeBtn);
    await interaction.editWithComponents(embed, [row]);
  }
}
