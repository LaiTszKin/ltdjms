import { type DiscordRuntimeGateway } from '@ltdjms/shared';
import { type EscortDispatchOrder, SourceType } from '../domain/index.js';
import { type DispatchAfterSalesStaffService } from '../service/dispatch-after-sales-staff.service.js';

/** Embed color constants matching DispatchPanelView palette. */
const COLOR_INFO = 0x57f287;
const COLOR_WARNING = 0xfee75c;
const COLOR_ERROR = 0xed4245;

/** Shape accepted by discord.js DMChannel.send() for embeds. */
interface EmbedPayload {
  title?: string;
  description?: string;
  color?: number;
  fields?: { name: string; value: string; inline?: boolean }[];
  footer?: { text: string };
}

/**
 * Best-effort DM notification service for escort dispatch events.
 * All methods catch errors internally and never throw.
 */
export class DispatchNotificationService {
  constructor(
    private readonly gateway: DiscordRuntimeGateway,
    private readonly afterSalesStaffService?: DispatchAfterSalesStaffService,
  ) {}

  /** DM 給護航者：有新的派單待確認（embed 格式）。 */
  async notifyEscortOrderCreated(order: EscortDispatchOrder): Promise<boolean> {
    return this.sendDMEmbed(String(order.escortUserId), {
      title: `📋 新護航訂單 #${order.orderNumber}`,
      description: '您有新的護航訂單待確認，請前往面板查看詳情。',
      color: COLOR_INFO,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
      ],
      footer: { text: '請前往面板確認接單' },
    });
    // TODO: Add button components for quick actions
  }

  /** DM 給護航者：已被指派新訂單（embed 格式）。 */
  async notifyEscortAssigned(order: EscortDispatchOrder): Promise<boolean> {
    return this.sendDMEmbed(String(order.escortUserId), {
      title: `📌 已指派訂單 #${order.orderNumber}`,
      description: '您已被指派護航訂單，請前往面板確認接單。',
      color: COLOR_WARNING,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
      ],
      footer: { text: '請前往面板確認接單' },
    });
    // TODO: Add button components for quick actions
  }

  /** DM 給管理員與客戶：護航者已確認接單（embed 格式）。 */
  async notifyEscortConfirmed(order: EscortDispatchOrder): Promise<boolean> {
    const embed: EmbedPayload = {
      title: `✅ 訂單已確認 #${order.orderNumber}`,
      description: '護航者已確認接單，訂單已進入進行中狀態。',
      color: COLOR_INFO,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
        { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
      ],
      footer: { text: '服務進行中' },
    };
    const results = await Promise.all([
      this.sendDMEmbed(String(order.assignedByUserId), embed),
      this.sendDMEmbed(String(order.customerUserId), embed),
    ]);
    return results.every(Boolean);
    // TODO: Add button components for quick actions
  }

  /** DM 給客戶：要求確認完成（embed 格式）。 */
  async notifyCompletionRequested(order: EscortDispatchOrder): Promise<boolean> {
    return this.sendDMEmbed(String(order.customerUserId), {
      title: `🔔 請確認完成 #${order.orderNumber}`,
      description: '護航者已完成服務，請前往面板確認完成。若 24 小時內未確認，系統將自動完成。',
      color: COLOR_WARNING,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '護航者', value: `<@${order.escortUserId}>`, inline: true },
      ],
      footer: { text: '24 小時未確認將視為訂單完成' },
    });
    // TODO: Add button components for quick actions
  }

  /** DM 給護航者：客戶已確認完成（embed 格式）。spec R6.1：僅通知護航者，不通知管理員。 */
  async notifyCustomerConfirmed(order: EscortDispatchOrder): Promise<boolean> {
    return this.sendDMEmbed(String(order.escortUserId), {
      title: `🎉 訂單已完成 #${order.orderNumber}`,
      description: '客戶已確認完成，訂單已完成。感謝您的服務！',
      color: COLOR_INFO,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '完成時間', value: order.completedAt?.toLocaleString('zh-TW') ?? 'N/A', inline: false },
      ],
      footer: { text: '訂單已完成' },
    });
    // TODO: Add button components for quick actions
  }

  /** DM 給售後人員：有新的售後案件（spec R7.4）。 */
  async notifyAfterSalesRequested(order: EscortDispatchOrder): Promise<boolean> {
    if (!this.afterSalesStaffService) {
      return false;
    }

    try {
      const result = await this.afterSalesStaffService.getStaffUserIds(order.guildId);
      if (!result.isOk()) {
        return false;
      }

      const staffIds = result.getValue();
      if (staffIds.size === 0) {
        return false;
      }

      const embed: EmbedPayload = {
        title: `🔧 售後申請 #${order.orderNumber}`,
        description: `客戶 <@${order.customerUserId}> 已提出售後申請，請前往面板處理。`,
        color: COLOR_WARNING,
        fields: [
          { name: '訂單編號', value: order.orderNumber, inline: true },
          { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
        ],
        footer: { text: '請前往面板承接售後案件' },
      };

      // R7.4: Filter by online status. Send to online staff first.
      // If none online, send to all staff.
      const onlineStaffIds = await this.filterOnlineStaff(order.guildId, [...staffIds]);
      const targetIds = onlineStaffIds.length > 0 ? onlineStaffIds : [...staffIds];

      await Promise.all(targetIds.map((staffId) => this.sendDMEmbed(String(staffId), embed)));
      return true;
    } catch {
      return false;
    }
    // TODO: Add button components for quick actions
  }

  /** DM 給客戶：售後案件已被接手（embed 格式）。 */
  async notifyAfterSalesClaimed(order: EscortDispatchOrder): Promise<boolean> {
    return this.sendDMEmbed(String(order.customerUserId), {
      title: `🛠️ 售後已接手 #${order.orderNumber}`,
      description: `您的售後案件已被售後人員接手處理。`,
      color: COLOR_INFO,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '售後人員', value: `<@${order.afterSalesAssigneeUserId}>`, inline: true },
      ],
      footer: { text: '售後處理中' },
    });
    // TODO: Add button components for quick actions
  }

  /** DM 給客戶：售後案件已結案（embed 格式）。 */
  async notifyAfterSalesClosed(order: EscortDispatchOrder): Promise<boolean> {
    return this.sendDMEmbed(String(order.customerUserId), {
      title: `✅ 售後已結案 #${order.orderNumber}`,
      description: '您的售後案件已結案。若有其他問題請隨時聯繫我們。',
      color: COLOR_INFO,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
      ],
      footer: { text: '售後已結案' },
    });
    // TODO: Add button components for quick actions
  }

  // ---- Private Helpers ----

  private async sendDMEmbed(userId: string, embed: EmbedPayload): Promise<boolean> {
    try {
      const client = this.gateway.requireReadyClient() as {
        users: {
          fetch(id: string): Promise<{
            send(options: { embeds: EmbedPayload[] }): Promise<unknown>;
          } | null>;
        };
      };
      const user = await client.users.fetch(userId);
      if (user != null) {
        await user.send({ embeds: [embed] });
        return true;
      }
      return false;
    } catch (e) {
      console.warn(
        `Failed to send DM to user ${userId}:`,
        e instanceof Error ? e.message : e,
      );
      return false;
    }
  }

  /**
   * Filters staff user IDs to those who are currently online in the guild.
   * Uses the discord.js Guild member cache (requires GuildPresences intent).
   */
  private async filterOnlineStaff(guildId: number, staffIds: number[]): Promise<number[]> {
    const onlineIds: number[] = [];
    for (const staffId of staffIds) {
      try {
        const isOnline = await this.isMemberOnline(String(guildId), String(staffId));
        if (isOnline) {
          onlineIds.push(staffId);
        }
      } catch {
        // If we can't check presence, exclude from online list
      }
    }
    return onlineIds;
  }

  private async isMemberOnline(guildId: string, userId: string): Promise<boolean> {
    try {
      const guild = this.gateway.findGuild(guildId) as {
        members: {
          fetch(id: string): Promise<{ presence?: { status: string } | null }>;
        };
      } | null;
      if (!guild) return false;
      const member = await guild.members.fetch(userId);
      return member.presence?.status === 'online';
    } catch {
      return false;
    }
  }
}
