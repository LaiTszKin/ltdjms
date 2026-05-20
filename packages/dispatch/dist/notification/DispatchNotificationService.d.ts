import { type DiscordRuntimeGateway } from '@ltdjms/shared';
import { type EscortDispatchOrder } from '../domain/index.js';
import { type DispatchAfterSalesStaffService } from '../service/dispatch-after-sales-staff.service.js';
/**
 * Best-effort DM notification service for escort dispatch events.
 * All methods catch errors internally and never throw.
 */
export declare class DispatchNotificationService {
    private readonly gateway;
    private readonly afterSalesStaffService?;
    constructor(gateway: DiscordRuntimeGateway, afterSalesStaffService?: DispatchAfterSalesStaffService | undefined);
    /** DM 給護航者：有新的派單待確認（embed 格式）。 */
    notifyEscortOrderCreated(order: EscortDispatchOrder): Promise<boolean>;
    /** DM 給護航者：已被指派新訂單（embed 格式）。 */
    notifyEscortAssigned(order: EscortDispatchOrder): Promise<boolean>;
    /** DM 給管理員與客戶：護航者已確認接單（embed 格式）。 */
    notifyEscortConfirmed(order: EscortDispatchOrder): Promise<boolean>;
    /** DM 給客戶：要求確認完成（embed 格式）。 */
    notifyCompletionRequested(order: EscortDispatchOrder): Promise<boolean>;
    /** DM 給護航者：客戶已確認完成（embed 格式）。spec R6.1：僅通知護航者，不通知管理員。 */
    notifyCustomerConfirmed(order: EscortDispatchOrder): Promise<boolean>;
    /** DM 給售後人員：有新的售後案件（spec R7.4）。 */
    notifyAfterSalesRequested(order: EscortDispatchOrder): Promise<boolean>;
    /** DM 給客戶：售後案件已被接手（embed 格式）。 */
    notifyAfterSalesClaimed(order: EscortDispatchOrder): Promise<boolean>;
    /** DM 給客戶：售後案件已結案（embed 格式）。 */
    notifyAfterSalesClosed(order: EscortDispatchOrder): Promise<boolean>;
    private sendDMEmbed;
    /**
     * Filters staff user IDs to those who are currently online in the guild.
     * Uses the discord.js Guild member cache (requires GuildPresences intent).
     */
    private filterOnlineStaff;
    private isMemberOnline;
}
