import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import pino from 'pino';
export declare class EscortOrderBuyerNotificationService {
    private readonly discordRuntimeGateway;
    private readonly log;
    constructor(discordRuntimeGateway: DiscordRuntimeGateway, logger?: pino.Logger);
    notifyEscortOrderCreated(order: any): void;
    buildEscortOrderCreatedMessage(order: any): string;
    private resolveSelfUserId;
    private formatPaymentMethod;
}
