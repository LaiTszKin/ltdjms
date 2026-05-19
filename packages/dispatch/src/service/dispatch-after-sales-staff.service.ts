import type { Result } from '@ltdjms/shared';
import { Ok, Err, DomainError, okVoid, type Unit } from '@ltdjms/shared';

import type { DispatchAfterSalesStaffRepo } from '../repo/dispatch-after-sales-staff.repo.js';

/**
 * 售後人員設定服務。
 * Matches Java DispatchAfterSalesStaffService exactly.
 */
export class DispatchAfterSalesStaffService {
  constructor(private readonly repository: DispatchAfterSalesStaffRepo) {}

  async getStaffUserIds(guildId: number): Promise<Result<Set<number>, DomainError>> {
    try {
      const staff = await this.repository.findStaffUserIds(guildId);
      return new Ok(staff);
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('查詢售後人員失敗', err));
    }
  }

  async addStaff(guildId: number, userId: number): Promise<Result<Unit, DomainError>> {
    try {
      const inserted = await this.repository.addStaff(guildId, userId);
      if (!inserted) {
        return new Err(DomainError.invalidInput('該成員已在售後名單中'));
      }
      return okVoid();
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('新增售後人員失敗', err));
    }
  }

  async removeStaff(guildId: number, userId: number): Promise<Result<Unit, DomainError>> {
    try {
      const removed = await this.repository.removeStaff(guildId, userId);
      if (!removed) {
        return new Err(DomainError.invalidInput('該成員不在售後名單中'));
      }
      return okVoid();
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      return new Err(DomainError.persistenceFailure('移除售後人員失敗', err));
    }
  }

  async isAfterSalesStaff(guildId: number, userId: number): Promise<boolean> {
    try {
      const staff = await this.repository.findStaffUserIds(guildId);
      return staff.has(userId);
    } catch {
      return false;
    }
  }
}
