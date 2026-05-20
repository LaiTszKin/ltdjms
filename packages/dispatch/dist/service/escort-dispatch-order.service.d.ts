import type { Result } from '@ltdjms/shared';
import { DomainError } from '@ltdjms/shared';
import type { EscortDispatchOrderRepo } from '../repo/escort-dispatch-order.repo.js';
import { EscortDispatchOrderNumberGenerator } from '../domain/order-number-generator.js';
import type { EscortOptionCatalogRepository } from './escort-option-pricing.service.js';
import { type EscortDispatchOrder } from '../domain/index.js';
/**
 * 派單護航訂單核心服務。
 * Matches Java EscortDispatchOrderService exactly.
 */
export declare class EscortDispatchOrderService {
    private readonly repository;
    private readonly orderNumberGenerator?;
    private readonly clock?;
    private readonly catalogRepository?;
    constructor(repository: EscortDispatchOrderRepo, orderNumberGenerator?: EscortDispatchOrderNumberGenerator | undefined, clock?: (() => number) | undefined, catalogRepository?: EscortOptionCatalogRepository | undefined);
    /** 建立待確認的新派單訂單。 */
    createOrder(guildId: number, assignedByUserId: number, escortUserId: number, customerUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 手動建立尚未派發給護航者的護航訂單。 */
    createManualOpenOrder(guildId: number, assignedByUserId: number, customerUserId: number, escortOptionCode: string): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 將既有待派發護航訂單派給指定護航者。 */
    assignPendingOrder(orderNumber: string, assignedByUserId: number, escortUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 護航者確認接單。 */
    confirmOrder(orderNumber: string, confirmerUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 護航者完成服務，等待客戶確認。 */
    requestCompletion(orderNumber: string, escortUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 客戶確認完成。 */
    customerConfirmCompletion(orderNumber: string, customerUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 客戶提出售後申請。 */
    requestAfterSales(orderNumber: string, customerUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 售後人員接手案件。 */
    claimAfterSales(orderNumber: string, afterSalesUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 售後人員完成結案。 */
    closeAfterSales(orderNumber: string, afterSalesUserId: number): Promise<Result<EscortDispatchOrder, DomainError>>;
    /** 查詢最近訂單（預設 10 筆）。 */
    findRecentOrders(guildId: number, limit?: number): Promise<Result<EscortDispatchOrder[], DomainError>>;
    /** 查詢尚未指定護航者的自動交接訂單（預設 5 筆）。 */
    findPendingAssignmentOrders(guildId: number, limit?: number): Promise<Result<EscortDispatchOrder[], DomainError>>;
    private findOrder;
    private ensureTimeoutCompletion;
    private normalizeLimit;
    private generateUniqueOrderNumber;
}
