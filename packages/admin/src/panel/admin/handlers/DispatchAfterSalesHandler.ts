import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { type DispatchAfterSalesStaffService } from '@ltdjms/dispatch';

/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export class DispatchAfterSalesHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_dispatch';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly afterSalesStaffService: DispatchAfterSalesStaffService,
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

    // Branch on sub-action
    if (fullCustomId === 'admin_dispatch_add') {
      // TODO: show member select for adding staff
      await this.showStaffList(interaction, guildId);
      return;
    }
    if (fullCustomId === 'admin_dispatch_remove') {
      // TODO: show member select for removing staff
      await this.showStaffList(interaction, guildId);
      return;
    }

    // Default: show staff list overview
    await this.showStaffList(interaction, guildId);
  }

  private async showStaffList(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const result = await this.afterSalesStaffService.getStaffUserIds(Number(guildId));

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
      .setColor(0x5865F2);
    await interaction.editEmbed(embed);
  }
}
