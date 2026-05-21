import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import type { Product } from '../domain/product-types.js';
import pino from 'pino';

export class ShopAdminNotificationService {
  private readonly log: pino.Logger;

  constructor(
    private readonly discordRuntimeGateway: DiscordRuntimeGateway,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  /**
   * Adapter for the AdminOrderNotifier interface used by FiatOrderPostPaymentWorker (P1-10).
   * Delegates to the existing escort notification builder.
   */
  notifyAdminsOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void {
    if (!dispatchOrder) return;
    const guildName = this.getGuildName(guildId);
    const message = this.buildAdminEscortNotification(guildId, buyerUserId, dispatchOrder, guildName ?? undefined);
    this.notifyGuildAdmins(guildId, message);
  }

  /**
   * Notifies admins about a new product order with product details.
   * This method is kept for direct use (e.g., manual dispatch).
   * The FiatOrderPostPaymentWorker uses the 3-parameter overload above.
   */
  notifyAdminsProductOrderCreated(
    guildId: number,
    buyerUserId: number,
    product: Product,
    orderType: string,
    orderReference: string,
  ): void {
    if (!product) return;

    const guildName = this.getGuildName(guildId);
    const message = this.buildAdminOrderNotification(
      guildId,
      buyerUserId,
      product,
      orderType,
      orderReference,
      guildName ?? undefined,
    );
    this.notifyGuildAdmins(guildId, message);
  }

  notifyAdminsEscortOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void {
    if (!dispatchOrder) return;

    const guildName = this.getGuildName(guildId);
    const message = this.buildAdminEscortNotification(guildId, buyerUserId, dispatchOrder, guildName ?? undefined);
    this.notifyGuildAdmins(guildId, message);
  }

  private notifyGuildAdmins(guildId: number, message: string): void {
    const client: any = this.discordRuntimeGateway.requireReadyClient();
    const selfUserId = this.discordRuntimeGateway.selfUserId();
    const notified = new Set<string>();

    try {
      const guild = client.guilds.cache.get(guildId.toString());
      if (!guild) {
        this.log.warn({ guildId }, 'Guild not found when notifying admins');
        return;
      }

      // NOTE: This iterates all cached guild members to find admins via permission check.
      // This is a fire-and-forget notification and is acceptable for typical guild sizes.
      // If a large guild experiences performance issues, consider using Discord's
      // permission-based member search API instead of iterating the entire cache.
      const members = guild.members.cache;
      if (members && members.size > 0) {
        for (const [, member] of members) {
          if (!this.isAdmin(member)) continue;
          const adminUserId = member.user.id;
          if (selfUserId && adminUserId === selfUserId) {
            this.log.debug({ guildId, selfUserId }, 'Skipping bot self when notifying admins');
            continue;
          }
          if (notified.has(adminUserId)) continue;
          notified.add(adminUserId);
          this.sendAdminNotification(member.user, message);
        }
      }

      // Also notify guild owner if not already notified
      const ownerId = guild.ownerId;
      if (
        ownerId &&
        (!selfUserId || ownerId !== selfUserId) &&
        !notified.has(ownerId)
      ) {
        guild.members.fetch(ownerId).then(
          (ownerMember: any) => {
            if (ownerMember && ownerMember.user) {
              const ownerUserId = ownerMember.user.id;
              if (selfUserId && ownerUserId === selfUserId) return;
              this.sendAdminNotification(ownerMember.user, message);
            }
          },
          (err: any) =>
            this.log.debug(
              { guildId, ownerId, error: err },
              'Failed to retrieve guild owner for order notification',
            ),
        );
      }
    } catch (e) {
      this.log.warn({ guildId, error: e }, 'Failed to notify guild admins');
    }
  }

  private sendAdminNotification(user: any, message: string): void {
    try {
      user.send(message).catch((err: any) => {
        this.log.warn(
          { adminUserId: user.id, error: err },
          'Failed to send admin DM for order notification',
        );
      });
    } catch (e) {
      this.log.warn(
        { adminUserId: user.id, error: e },
        'Failed to open admin DM for order notification',
      );
    }
  }

  private getGuildName(guildId: number): string | null {
    try {
      const client: any = this.discordRuntimeGateway.requireReadyClient();
      const guild = client.guilds.cache.get(guildId.toString());
      return guild?.name ?? null;
    } catch {
      return null;
    }
  }

  private buildAdminOrderNotification(
    guildId: number,
    buyerUserId: number,
    product: Product,
    orderType: string,
    orderReference: string,
    guildName?: string,
  ): string {
    const lines: string[] = [];
    lines.push('📩 有新訂單發起，請儘速派單\n');
    if (guildName) {
      lines.push(`**伺服器：** ${guildName}（\`${guildId}\`）`);
    } else {
      lines.push(`**伺服器：** \`${guildId}\``);
    }
    lines.push(`**買家：** <@${buyerUserId}>`);
    lines.push(`**商品：** ${product.name}`);
    lines.push(`**訂單類型：** ${orderType}`);
    if (orderReference) {
      lines.push(`**訂單編號：** \`${orderReference}\``);
    }
    lines.push('\n請使用 `/dispatch-panel` 進行派單分配。');
    return lines.join('\n');
  }

  private buildAdminEscortNotification(
    guildId: number,
    buyerUserId: number,
    order: any,
    guildName?: string,
  ): string {
    const lines: string[] = [];
    lines.push('📩 有新護航工作交接，請儘速處理\n');
    if (guildName) {
      lines.push(`**伺服器：** ${guildName}（\`${guildId}\`）`);
    } else {
      lines.push(`**伺服器：** \`${guildId}\``);
    }
    lines.push(`**買家：** <@${buyerUserId}>`);
    lines.push(`**來源類型：** ${this.describeSourceType(order.sourceType)}`);
    if (order.sourceReference) {
      lines.push(`**來源參考：** \`${order.sourceReference}\``);
    }
    if (order.sourceProductName) {
      lines.push(`**來源商品：** ${order.sourceProductName}`);
    }
    if (order.sourceEscortOptionCode) {
      lines.push(`**護航選項：** \`${order.sourceEscortOptionCode}\``);
    }
    lines.push(`**Dispatch 編號：** \`${order.orderNumber}\``);
    lines.push('\n請使用 `/dispatch-panel` 檢視或後續處理此工作項。');
    return lines.join('\n');
  }

  private isAdmin(member: any): boolean {
    if (!member) return false;
    try {
      return member.permissions.has('Administrator');
    } catch {
      return false;
    }
  }

  private describeSourceType(sourceType: string | null): string {
    if (!sourceType) return '未知';
    switch (sourceType) {
      case 'MANUAL':
        return '手動派單';
      case 'CURRENCY_PURCHASE':
        return '貨幣購買';
      case 'FIAT_PAYMENT':
        return '法幣付款';
      default:
        return '未知';
    }
  }
}
