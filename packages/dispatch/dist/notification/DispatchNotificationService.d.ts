import { type DiscordRuntimeGateway } from '@ltdjms/shared';
import { type EscortDispatchOrder } from '../domain/index.js';
/**
 * Best-effort DM notification service for escort dispatch events.
 * All methods catch errors internally and never throw.
 */
export declare class DispatchNotificationService {
    private readonly gateway;
    constructor(gateway: DiscordRuntimeGateway);
    /** DM 給護航者：有新的派單待確認。 */
    notifyEscortOrderCreated(order: EscortDispatchOrder): Promise<void>;
    /** DM 給護航者：已被指派新訂單。 */
    notifyEscortAssigned(order: EscortDispatchOrder): Promise<void>;
    /** DM 給管理員與客戶：護航者已確認接單。 */
    notifyEscortConfirmed(order: EscortDispatchOrder): Promise<void>;
    /** DM 給客戶：要求確認完成。 */
    notifyCompletionRequested(order: EscortDispatchOrder): Promise<void>;
    /** DM 給護航者與管理員：客戶已確認完成。 */
    notifyCustomerConfirmed(order: EscortDispatchOrder): Promise<void>;
    /** DM 給售後人員：有新的售後案件。 */
    notifyAfterSalesRequested(order: EscortDispatchOrder): Promise<void>;
    /** DM 給客戶：售後案件已被接手。 */
    notifyAfterSalesClaimed(order: EscortDispatchOrder): Promise<void>;
    /** DM 給客戶：售後案件已結案。 */
    notifyAfterSalesClosed(order: EscortDispatchOrder): Promise<void>;
    private sendDM;
    /**
     * Sends a notification to the guild's system channel as a fallback
     * when no specific user DM is available (e.g., after-sales staff pool).
     */
    private sendDMToStaffGuild;
}
