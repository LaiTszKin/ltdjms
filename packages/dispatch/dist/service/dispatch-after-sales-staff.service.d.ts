import type { Result } from '@ltdjms/shared';
import { DomainError, type Unit } from '@ltdjms/shared';
import type { DispatchAfterSalesStaffRepo } from '../repo/dispatch-after-sales-staff.repo.js';
/**
 * 售後人員設定服務。
 * Matches Java DispatchAfterSalesStaffService exactly.
 */
export declare class DispatchAfterSalesStaffService {
    private readonly repository;
    constructor(repository: DispatchAfterSalesStaffRepo);
    getStaffUserIds(guildId: number): Promise<Result<Set<number>, DomainError>>;
    addStaff(guildId: number, userId: number): Promise<Result<Unit, DomainError>>;
    removeStaff(guildId: number, userId: number): Promise<Result<Unit, DomainError>>;
    isAfterSalesStaff(guildId: number, userId: number): Promise<boolean>;
}
