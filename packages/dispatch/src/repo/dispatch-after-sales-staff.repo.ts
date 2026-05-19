/**
 * 派單系統售後人員設定的持久化介面。
 */
export interface DispatchAfterSalesStaffRepo {
  /** 查詢 guild 設定的所有售後人員 userId。 */
  findStaffUserIds(guildId: number): Promise<Set<number>>;

  /**
   * 新增售後人員。
   * @returns true 表示新增成功；false 表示已存在
   */
  addStaff(guildId: number, userId: number): Promise<boolean>;

  /**
   * 移除售後人員。
   * @returns true 表示移除成功；false 表示不存在
   */
  removeStaff(guildId: number, userId: number): Promise<boolean>;
}
