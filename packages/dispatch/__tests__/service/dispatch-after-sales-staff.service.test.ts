import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isOk, isErr } from '@ltdjms/shared';
import { DispatchAfterSalesStaffService } from '../../src/service/dispatch-after-sales-staff.service.js';
import type { DispatchAfterSalesStaffRepo } from '../../src/repo/dispatch-after-sales-staff.repo.js';

describe('DispatchAfterSalesStaffService', () => {
  let mockRepo: DispatchAfterSalesStaffRepo;
  let service: DispatchAfterSalesStaffService;

  beforeEach(() => {
    mockRepo = {
      findStaffUserIds: vi.fn(),
      addStaff: vi.fn(),
      removeStaff: vi.fn(),
    };
    service = new DispatchAfterSalesStaffService(mockRepo);
  });

  describe('getStaffUserIds', () => {
    it('should return staff user IDs', async () => {
      vi.mocked(mockRepo.findStaffUserIds).mockResolvedValue(new Set([100, 200, 300]));

      const result = await service.getStaffUserIds(1);

      expect(isOk(result)).toBe(true);
      if (isOk(result)) {
        expect(result.getValue()).toEqual(new Set([100, 200, 300]));
      }
    });
  });

  describe('addStaff', () => {
    it('should add a staff member', async () => {
      vi.mocked(mockRepo.addStaff).mockResolvedValue(true);

      const result = await service.addStaff(1, 100);

      expect(isOk(result)).toBe(true);
    });

    it('should return error if staff already exists', async () => {
      vi.mocked(mockRepo.addStaff).mockResolvedValue(false);

      const result = await service.addStaff(1, 100);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('removeStaff', () => {
    it('should remove a staff member', async () => {
      vi.mocked(mockRepo.removeStaff).mockResolvedValue(true);

      const result = await service.removeStaff(1, 100);

      expect(isOk(result)).toBe(true);
    });

    it('should return error if staff not in list', async () => {
      vi.mocked(mockRepo.removeStaff).mockResolvedValue(false);

      const result = await service.removeStaff(1, 999);

      expect(isErr(result)).toBe(true);
    });
  });

  describe('isAfterSalesStaff', () => {
    it('should return true for staff members', async () => {
      vi.mocked(mockRepo.findStaffUserIds).mockResolvedValue(new Set([100, 200]));

      const result = await service.isAfterSalesStaff(1, 100);

      expect(result).toBe(true);
    });

    it('should return false for non-staff members', async () => {
      vi.mocked(mockRepo.findStaffUserIds).mockResolvedValue(new Set([100, 200]));

      const result = await service.isAfterSalesStaff(1, 999);

      expect(result).toBe(false);
    });

    it('should return false on exception (safe default)', async () => {
      vi.mocked(mockRepo.findStaffUserIds).mockRejectedValue(new Error('DB error'));

      const result = await service.isAfterSalesStaff(1, 100);

      expect(result).toBe(false);
    });
  });
});
