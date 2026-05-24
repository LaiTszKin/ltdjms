import { describe, it, expect, vi } from 'vitest';
import { ok } from '@ltdjms/shared';
import { UserPanelService } from '../UserPanelService.js';
import type { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
import { USER_PANEL_PAGE_SIZE } from '../../constants/UserPanelConstants.js';

/** Pagination clamp regression: downstream clamped pages pass through to callers. */
describe('UserPanelService pagination', () => {
  it('should return clamped token history page from facade', async () => {
    const clampedPage = {
      transactions: [],
      currentPage: 3,
      totalPages: 3,
      totalCount: 25,
      pageSize: USER_PANEL_PAGE_SIZE,
    };

    const memberInfoFacade = {
      getTokenTransactionPage: vi.fn().mockResolvedValue(ok(clampedPage)),
    } as unknown as MemberInfoFacade;

    const service = new UserPanelService(memberInfoFacade);
    const page = await service.getTokenTransactionPage('1', '2', 99);

    expect(memberInfoFacade.getTokenTransactionPage).toHaveBeenCalledWith(
      '1',
      '2',
      99,
      USER_PANEL_PAGE_SIZE,
    );
    expect(page.currentPage).toBe(3);
    expect(page.totalPages).toBe(3);
  });
});
