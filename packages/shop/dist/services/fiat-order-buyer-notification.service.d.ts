import type { FiatOrder } from '../domain/fiat-order.js';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import pino from 'pino';
export declare class FiatOrderBuyerNotificationService {
    private readonly discordRuntimeGateway;
    private readonly log;
    constructor(discordRuntimeGateway: DiscordRuntimeGateway, logger?: pino.Logger);
    notifyPaymentSucceeded(order: FiatOrder): void;
    buildPaymentSucceededMessage(order: FiatOrder): string;
}
