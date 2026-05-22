import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import type { DispatchOrderSnapshot } from '../domain/escort-dispatch-handoff-service.js';
import pino from 'pino';

/** Minimal discord.js User shape used by this service. */
interface DiscordJsUser {
  id: string;
  send(message: string): Promise<unknown>;
}

/** Minimal discord.js Client shape used by this service. */
interface DiscordJsClient {
  users: {
    fetch(userId: string): Promise<DiscordJsUser>;
  };
}

export class EscortOrderBuyerNotificationService {
  private readonly log: pino.Logger;

  constructor(
    private readonly discordRuntimeGateway: DiscordRuntimeGateway,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  notifyEscortOrderCreated(order: DispatchOrderSnapshot): void {
    if (!order) return;

    // Skip notification if the buyer is the bot itself
    const selfUserId = this.resolveSelfUserId();
    if (selfUserId !== null && String(order.customerUserId) === selfUserId) {
      this.log.debug({ userId: selfUserId }, 'Skipping buyer escort notification for bot self');
      return;
    }

    try {
      const client = this.discordRuntimeGateway.requireReadyClient() as DiscordJsClient;
      const message = this.buildEscortOrderCreatedMessage(order);

      client.users.fetch(order.customerUserId.toString()).then(
        (buyerUser) => {
          buyerUser
            .send(message)
            .catch((err: unknown) =>
              this.log.warn(
                { orderNumber: order.orderNumber, buyerUserId: order.customerUserId, error: err },
                'Failed to DM buyer escort order created',
              ),
            );
        },
        (err: unknown) =>
          this.log.warn(
            { orderNumber: order.orderNumber, buyerUserId: order.customerUserId, error: err },
            'Failed to retrieve buyer user for escort order notification',
          ),
      );
    } catch (e) {
      this.log.warn(
        { orderNumber: order.orderNumber, buyerUserId: order.customerUserId, error: e },
        'Unexpected error while notifying buyer escort order created',
      );
    }
  }

  buildEscortOrderCreatedMessage(order: DispatchOrderSnapshot): string {
    const lines: string[] = [];
    lines.push('🛡️ 護航訂單已建立，正在等待處理\n');
    if (order.sourceProductName) {
      lines.push(`**商品：** ${order.sourceProductName}`);
    }
    lines.push(`**護航訂單編號：** \`${order.orderNumber}\``);
    lines.push(`**付款方式：** ${this.formatPaymentMethod(order)}`);
    lines.push('\n我們已收到你的訂單，管理員將會在不久後為你安排護航，請耐心等候。');
    return lines.join('\n');
  }

  private resolveSelfUserId(): string | null {
    try {
      return this.discordRuntimeGateway.selfUserId();
    } catch {
      return null;
    }
  }

  private formatPaymentMethod(order: DispatchOrderSnapshot): string {
    const sourceType = order.sourceType;
    if (!sourceType) return '未知';

    switch (sourceType) {
      case 'CURRENCY_PURCHASE':
        if (order.sourceCurrencyPrice && order.sourceCurrencyPrice > 0) {
          return `貨幣（${order.sourceCurrencyPrice.toLocaleString()} 貨幣）`;
        }
        return '貨幣';
      case 'FIAT_PAYMENT':
        if (order.sourceFiatPriceTwd && order.sourceFiatPriceTwd > 0) {
          return `法幣（NT$${order.sourceFiatPriceTwd.toLocaleString()}）`;
        }
        return '法幣';
      case 'MANUAL':
        return '手動派單';
      default:
        return '未知';
    }
  }
}
