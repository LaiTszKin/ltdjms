import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import type { Product } from '../domain/product-types.js';
import pino from 'pino';
export declare class ShopAdminNotificationService {
    private readonly discordRuntimeGateway;
    private readonly log;
    constructor(discordRuntimeGateway: DiscordRuntimeGateway, logger?: pino.Logger);
    notifyAdminsOrderCreated(guildId: number, buyerUserId: number, product: Product, orderType: string, orderReference: string): void;
    notifyAdminsEscortOrderCreated(guildId: number, buyerUserId: number, dispatchOrder: any): void;
    private notifyGuildAdmins;
    private sendAdminNotification;
    private buildAdminOrderNotification;
    private buildAdminEscortNotification;
    private isAdmin;
    private describeSourceType;
}
