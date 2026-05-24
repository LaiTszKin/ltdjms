import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import type { DispatchOrderSnapshot } from '../domain/escort-dispatch-handoff-service.js';
import type { Product } from '../domain/product-types.js';
import pino from 'pino';

/** Minimal discord.js User shape used by this service. */
interface DiscordJsUser {
  id: string;
  send(message: string): Promise<unknown>;
}

/** Minimal discord.js GuildMember shape used by this service. */
interface DiscordJsGuildMember {
  user: DiscordJsUser;
}

/** Minimal discord.js Role shape used by this service. */
interface DiscordJsRole {
  permissions: {
    has(permission: string): boolean;
  };
  members: Map<string, DiscordJsGuildMember>;
}

/** Minimal discord.js Guild shape used by this service. */
interface DiscordJsGuild {
  name: string;
  ownerId: string;
  roles: {
    cache: Map<string, DiscordJsRole> & {
      find(predicate: (role: DiscordJsRole) => boolean): DiscordJsRole | undefined;
    };
  };
  members: {
    fetch(userId: string): Promise<DiscordJsGuildMember>;
  };
}

/** Minimal discord.js Client shape used by this service. */
interface DiscordJsClient {
  guilds: {
    cache: Map<string, DiscordJsGuild>;
  };
  users: {
    fetch(userId: string): Promise<DiscordJsUser>;
  };
}

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
  notifyAdminsOrderCreated(
    guildId: number,
    buyerUserId: number,
    dispatchOrder: DispatchOrderSnapshot,
  ): void {
    if (!dispatchOrder) return;
    const guildName = this.getGuildName(guildId);
    const message = this.buildAdminEscortNotification(
      guildId,
      buyerUserId,
      dispatchOrder,
      guildName ?? undefined,
    );
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

  notifyAdminsEscortOrderCreated(
    guildId: number,
    buyerUserId: number,
    dispatchOrder: DispatchOrderSnapshot,
  ): void {
    if (!dispatchOrder) return;

    const guildName = this.getGuildName(guildId);
    const message = this.buildAdminEscortNotification(
      guildId,
      buyerUserId,
      dispatchOrder,
      guildName ?? undefined,
    );
    this.notifyGuildAdmins(guildId, message);
  }

  private notifyGuildAdmins(guildId: number, message: string): void {
    const client = this.discordRuntimeGateway.requireReadyClient() as DiscordJsClient;
    const selfUserId = this.discordRuntimeGateway.selfUserId();
    const notified = new Set<string>();

    try {
      const guild = client.guilds.cache.get(guildId.toString());
      if (!guild) {
        this.log.warn({ guildId }, 'Guild not found when notifying admins');
        return;
      }

      // Find members with ADMINISTRATOR permission via roles rather than iterating all guild members
      const adminMembers = new Set<DiscordJsGuildMember>();
      const adminRole = guild.roles.cache.find((role) => role.permissions.has('Administrator'));
      if (adminRole) {
        for (const [, member] of adminRole.members) {
          adminMembers.add(member);
        }
      }

      for (const member of adminMembers) {
        const adminUserId = member.user.id;
        if (selfUserId && adminUserId === selfUserId) {
          this.log.debug({ guildId, selfUserId }, 'Skipping bot self when notifying admins');
          continue;
        }
        if (notified.has(adminUserId)) continue;
        notified.add(adminUserId);
        this.sendAdminNotification(member.user, message);
      }

      // Also notify guild owner if not already notified
      const ownerId = guild.ownerId;
      if (ownerId && (!selfUserId || ownerId !== selfUserId) && !notified.has(ownerId)) {
        guild.members.fetch(ownerId).then(
          (ownerMember) => {
            if (ownerMember && ownerMember.user) {
              const ownerUserId = ownerMember.user.id;
              if (selfUserId && ownerUserId === selfUserId) return;
              this.sendAdminNotification(ownerMember.user, message);
            }
          },
          (err: unknown) =>
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

  private sendAdminNotification(user: DiscordJsUser, message: string): void {
    try {
      user.send(message).catch((err: unknown) => {
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
      const client = this.discordRuntimeGateway.requireReadyClient() as DiscordJsClient;
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
    order: DispatchOrderSnapshot,
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
    lines.push(`**來源類型：** ${this.describeSourceType(order.sourceType ?? null)}`);
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
