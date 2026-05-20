import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import type { Product } from '../domain/product-types.js';
import pino from 'pino';
export declare class ShopAdminNotificationService {
    private readonly discordRuntimeGateway;
    private readonly log;
    constructor(discordRuntimeGateway: DiscordRuntimeGateway, logger?: pino.Logger);
    /**
     * Adapter for the AdminOrderNotifier interface used by FiatOrderPostPaymentWorker (P1-10).
     * Delegates to the existing escort notification builder.
     */
    notifyAdminsOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void;
    /**
     * Notifies admins about a new product order with product details.
     * This method is kept for direct use (e.g., manual dispatch).
     * The FiatOrderPostPaymentWorker uses the 3-parameter overload above.
     */
    notifyAdminsProductOrderCreated(guildId: number, buyerUserId: number, product: Product, orderType: string, orderReference: string): void;
    notifyAdminsEscortOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void;
    private notifyGuildAdmins;
    private sendAdminNotification;
    private buildAdminOrderNotification;
    private buildAdminEscortNotification;
    private isAdmin;
    private describeSourceType;
}
