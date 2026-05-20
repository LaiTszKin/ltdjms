import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { type DispatchAfterSalesStaffService } from '@ltdjms/dispatch';

/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export class DispatchAfterSalesHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_dispatch';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
    private readonly afterSalesStaffService: DispatchAfterSalesStaffService,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await interaction.deferReply();

    // Try to get after-sales staff list
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
