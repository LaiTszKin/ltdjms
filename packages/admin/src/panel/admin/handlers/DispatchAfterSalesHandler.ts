import {
  type DiscordInteraction,
  type DiscordContext,
  type DomainEventPublisher,
  type DispatchAfterSalesConfigChangedEvent,
} from '@ltdjms/shared';
import {
  EmbedBuilder,
  ActionRowBuilder,
  UserSelectMenuBuilder,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../../session/types.js';
import { BotErrorHandler } from '../../../commands/infra/BotErrorHandler.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';
import { BaseAdminHandler } from '../BaseAdminHandler.js';
import { type DispatchAfterSalesStaffService } from '@ltdjms/dispatch';
import { Colors } from '../../../constants/colors.js';

/**
 * Handler for dispatch after-sales config interactions (admin_dispatch_*).
 * Supports add/remove after-sales staff members.
 */
export class DispatchAfterSalesHandler extends BaseAdminHandler {
  readonly customIdPrefix = 'admin_dispatch';

  constructor(
    sessionManager: AdminPanelSessionManager,
    private readonly afterSalesStaffService: DispatchAfterSalesStaffService,
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
    const raw = interaction.getHook() as {
      editReply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<UserSelectMenuBuilder>[] }) => Promise<void>;
    };

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
    await raw.editReply({ embeds: [embed], components: [row] });
  }

  private async handleAddStaff(
    interaction: DiscordInteraction,
    guildId: string,
  ): Promise<void> {
    const raw = interaction.getHook() as { values?: string[] };
    const selectedIds = raw.values;
    if (!selectedIds || selectedIds.length === 0) {
      await this.showStaffList(interaction, guildId);
      return;
    }

    const staffId = selectedIds[0];
    const result = await this.afterSalesStaffService.addStaff(Number(guildId), Number(staffId));

    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'dispatch_after_sales_config_changed',
        guildId,
      } as DispatchAfterSalesConfigChangedEvent);

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
    const raw = interaction.getHook() as { values?: string[] };
    const selectedIds = raw.values;
    if (!selectedIds || selectedIds.length === 0) {
      await this.showStaffList(interaction, guildId);
      return;
    }

    const staffId = selectedIds[0];
    const result = await this.afterSalesStaffService.removeStaff(Number(guildId), Number(staffId));

    if (result.isOk()) {
      this.eventPublisher.publish({
        eventType: 'dispatch_after_sales_config_changed',
        guildId,
      } as DispatchAfterSalesConfigChangedEvent);

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
      .setColor(Colors.PRIMARY);
    await interaction.editEmbed(embed);
  }
}
