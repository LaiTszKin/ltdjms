import { processWithConcurrencyLimit } from '@ltdjms/shared';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import { type EscortDispatchOrder } from '../domain/index.js';
import { type DispatchAfterSalesStaffService } from '../service/dispatch-after-sales-staff.service.js';
import { COLOR_INFO, COLOR_WARNING, COLOR_ERROR } from '../constants.js';

/** Shape accepted by discord.js DMChannel.send() for embeds. */
interface EmbedPayload {
  title?: string;
  description?: string;
  color?: number;
  fields?: { name: string; value: string; inline?: boolean }[];
  footer?: { text: string };
}

/** Shape for ActionRow component (buttons). */
interface ButtonComponent {
  type: 2;
  style: number;
  custom_id: string;
  label: string;
}

interface ActionRowPayload {
  type: 1;
  components: ButtonComponent[];
}

/** The order-specific custom ID prefix for notification buttons. */
const NOTIFY_PREFIX = 'dispatch_notify_';
const NOTIFY_CONFIRM = `${NOTIFY_PREFIX}confirm`;
const NOTIFY_CONFIRM_COMPLETION = `${NOTIFY_PREFIX}confirm_completion`;
const NOTIFY_AFTER_SALES = `${NOTIFY_PREFIX}after_sales`;
const NOTIFY_CLAIM = `${NOTIFY_PREFIX}claim`;
const NOTIFY_CLOSE = `${NOTIFY_PREFIX}close`;

const MAX_NOTIFICATION_RECIPIENTS = 20;

/**
 * Best-effort DM notification service for escort dispatch events.
 * All methods catch errors internally and never throw.
 */
export class DispatchNotificationService {
  constructor(
    private readonly gateway: DiscordRuntimeGateway,
    private readonly afterSalesStaffService?: DispatchAfterSalesStaffService,
  ) {}

  /** DM 給護航者：已分配到新訂單（embed 格式），附「確認接單」按鈕。 */
  async notifyEscortAssigned(order: EscortDispatchOrder): Promise<boolean> {
    return this.sendDMEmbed(String(order.escortUserId), {
      title: `📋 新訂單已分配 #${order.orderNumber}`,
      description: '您已被分配一個新訂單，請點擊下方按鈕確認接單。',
      color: COLOR_INFO,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
      ],
      footer: { text: '請在時限內確認接單' },
    }, [
      {
        type: 1,
        components: [
          { type: 2, style: 3, custom_id: `${NOTIFY_CONFIRM}:${order.orderNumber}`, label: '確認接單' },
        ],
      },
    ]);
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
    // P0-3: DM 給客戶及護航者（不 DM 管理員，不附「送出完成」按鈕）
    const results = await Promise.all([
      this.sendDMEmbed(String(order.customerUserId), embed),
      // 護航者通知不含「送出完成」按鈕——該按鈕僅在 R5 (notifyCompletionRequested) 出現
      this.sendDMEmbed(String(order.escortUserId), {
        title: `✅ 已確認接單 #${order.orderNumber}`,
        description: '您已確認接單，請在服務完成後操作送出完成。',
        color: COLOR_INFO,
        fields: [
          { name: '訂單編號', value: order.orderNumber, inline: true },
          { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
        ],
        footer: { text: '服務進行中' },
      }),
    ]);
    return results.every(Boolean);
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
    }, [
      {
        type: 1,
        components: [
          { type: 2, style: 3, custom_id: `${NOTIFY_CONFIRM_COMPLETION}:${order.orderNumber}`, label: '確認完成' },
          { type: 2, style: 4, custom_id: `${NOTIFY_AFTER_SALES}:${order.orderNumber}`, label: '申請售後' },
        ],
      },
    ]);
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
      const filteredIds = onlineStaffIds.length > 0 ? onlineStaffIds : [...staffIds];
      // P2-18: 限制單次通知人數，避免多人同時收到 DM
      const targetIds = filteredIds.slice(0, MAX_NOTIFICATION_RECIPIENTS);

      await processWithConcurrencyLimit(targetIds, async (staffId) =>
        this.sendDMEmbed(String(staffId), embed, [
          { type: 1, components: [{ type: 2, style: 3, custom_id: `${NOTIFY_CLAIM}:${order.orderNumber}`, label: '承接售後' }] },
        ]),
      3);
      return true;
    } catch {
      return false;
    }
  }

  /** DM 給客戶與售後人員：售後案件已被接手（embed 格式）。 */
  async notifyAfterSalesClaimed(order: EscortDispatchOrder): Promise<boolean> {
    const customerResult = await this.sendDMEmbed(String(order.customerUserId), {
      title: `🛠️ 售後已接手 #${order.orderNumber}`,
      description: `您的售後案件已被售後人員接手處理。`,
      color: COLOR_INFO,
      fields: [
        { name: '訂單編號', value: order.orderNumber, inline: true },
        { name: '售後人員', value: `<@${order.afterSalesAssigneeUserId}>`, inline: true },
      ],
      footer: { text: '售後處理中' },
    });

    // R8: DM the after-sales staff with a "結案" button (P0-8)
    let staffResult = true;
    if (order.afterSalesAssigneeUserId != null) {
      staffResult = await this.sendDMEmbed(String(order.afterSalesAssigneeUserId), {
        title: `🛠️ 售後案件已接手 #${order.orderNumber}`,
        description: '您已接手此售後案件，處理完成後請點擊結案。',
        color: COLOR_INFO,
        fields: [
          { name: '訂單編號', value: order.orderNumber, inline: true },
          { name: '客戶', value: `<@${order.customerUserId}>`, inline: true },
        ],
        footer: { text: '售後處理中' },
      }, [
        { type: 1, components: [{ type: 2, style: 4, custom_id: `${NOTIFY_CLOSE}:${order.orderNumber}`, label: '結案' }] },
      ]);
    }

    return customerResult && staffResult;
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
  }

  // ---- Private Helpers ----

  private async sendDMEmbed(
    userId: string,
    embed: EmbedPayload,
    components?: ActionRowPayload[],
  ): Promise<boolean> {
    try {
      const options: Record<string, unknown> = { embeds: [embed] };
      if (components && components.length > 0) {
        options.components = components;
      }
      return await this.gateway.sendDM(userId, options);
    } catch (e) {
      console.warn(
        `Failed to send DM to user ${userId}:`,
        e instanceof Error ? e.message : e,
      );
      return false;
    }
  }

  /**
   * Filters staff user IDs to those who are currently online in the guild (parallel).
   * Uses the gateway to check member presence.
   */
  private async filterOnlineStaff(guildId: number, staffIds: number[]): Promise<number[]> {
    // P2-31/P3-21: 使用 Map 保留原始 staffIds 順序，避免 processWithConcurrencyLimit 回傳亂序
    const results = new Map<number, boolean>();
    await processWithConcurrencyLimit(staffIds, async (staffId) => {
      try {
        const isOnline = await this.gateway.isMemberOnline(String(guildId), String(staffId));
        results.set(staffId, isOnline);
      } catch {
        results.set(staffId, false);
      }
    }, 5);
    return staffIds.filter((id) => results.get(id) === true);
  }
}
