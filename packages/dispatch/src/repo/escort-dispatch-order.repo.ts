import type { EscortDispatchOrder, SourceType } from '../domain/index.js';

/**
 * 派單護航訂單的持久化介面。
 */
export interface EscortDispatchOrderRepo {
  /** 儲存新訂單並回傳帶有資料庫主鍵的實體。 */
  save(order: EscortDispatchOrder): Promise<EscortDispatchOrder>;

  /** 更新既有訂單並回傳最新狀態。 */
  update(order: EscortDispatchOrder): Promise<EscortDispatchOrder>;

  /** 依訂單編號查詢。 */
  findByOrderNumber(orderNumber: string): Promise<EscortDispatchOrder | null>;

  /** 依來源型別與來源參考查詢。 */
  findBySourceIdentity(
    sourceType: SourceType,
    sourceReference: string,
  ): Promise<EscortDispatchOrder | null>;

  /** 取得 guild 最近建立的訂單（依建立時間遞減）。 */
  findRecentByGuildId(guildId: number, limit: number): Promise<EscortDispatchOrder[]>;

  /** 取得 guild 尚未指定護航者的訂單（依建立時間遞增）。 */
  findPendingAssignmentByGuildId(guildId: number, limit: number): Promise<EscortDispatchOrder[]>;

  /**
   * 原子派發待指定護航者的訂單。
   * 僅在訂單狀態為 PENDING_CONFIRMATION 且 escort_user_id = 0 時成功。
   */
  assignEscort(
    orderNumber: string,
    assignedByUserId: number,
    escortUserId: number,
    assignedAt: Date,
  ): Promise<EscortDispatchOrder | null>;

  /**
   * 原子接手售後案件。
   * 僅在訂單狀態為 AFTER_SALES_REQUESTED 且尚未被接手時成功。
   */
  claimAfterSales(
    orderNumber: string,
    assigneeUserId: number,
    assignedAt: Date,
  ): Promise<EscortDispatchOrder | null>;

  /**
   * 原子售後結案。
   * 僅接手者本人且狀態為 AFTER_SALES_IN_PROGRESS 時成功。
   */
  closeAfterSales(
    orderNumber: string,
    assigneeUserId: number,
    closedAt: Date,
  ): Promise<EscortDispatchOrder | null>;

  /** 檢查訂單編號是否已存在。 */
  existsByOrderNumber(orderNumber: string): Promise<boolean>;
}
