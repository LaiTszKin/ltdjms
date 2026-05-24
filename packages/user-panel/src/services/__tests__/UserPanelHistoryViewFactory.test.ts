import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { UserPanelHistoryViewFactory } from '../UserPanelHistoryViewFactory.js';
import { UserPanelConstants } from '../../constants/UserPanelConstants.js';
import type { GameTokenTransaction } from '@ltdjms/games';
import { GameTokenTransactionSource } from '@ltdjms/games';

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), '../../__tests__/fixtures');
const oracle = JSON.parse(readFileSync(join(fixturesDir, 'java-history-oracle.json'), 'utf8'));

/** UT-203: UserPanelHistoryViewFactory pagination parity */
describe('UserPanelHistoryViewFactory (UT-203)', () => {
  it('should show empty token history state with page indicator footer', () => {
    const page = {
      transactions: [] as GameTokenTransaction[],
      currentPage: 1,
      totalPages: 1,
      totalCount: 0,
      pageSize: oracle.pageSize,
    };

    const embed = UserPanelHistoryViewFactory.buildTokenHistoryEmbed(page);

    expect(embed.title).toBe('📜 遊戲代幣流水');
    expect(embed.description).toBe(oracle.types.token.emptyDescription);
    expect(embed.footer).toBe(
      oracle.pageIndicatorPattern
        .replace('{current}', '1')
        .replace('{total}', '1')
        .replace('{count}', '0'),
    );
  });

  it('should include back, prev, and next pagination buttons on middle page', () => {
    const page = {
      transactions: [] as GameTokenTransaction[],
      currentPage: 2,
      totalPages: 3,
      totalCount: 30,
      pageSize: oracle.pageSize,
    };

    const row = UserPanelHistoryViewFactory.buildTokenPaginationButtons(page);
    const buttonIds = row.components.map((c) => c.toJSON().custom_id);

    expect(buttonIds).toEqual(oracle.pagination.examplePage2of3.buttonIds);
    expect(buttonIds[0]).toBe(UserPanelConstants.BUTTON_BACK_TO_PANEL);
    expect(buttonIds[1]).toBe(`${UserPanelConstants.BUTTON_PREFIX_TOKEN_PAGE}1`);
    expect(buttonIds[2]).toBe(`${UserPanelConstants.BUTTON_PREFIX_TOKEN_PAGE}3`);
  });

  it('should format non-empty token history lines', () => {
    const tx: GameTokenTransaction = {
      id: 1,
      guildId: 1,
      userId: '100',
      amount: 10,
      balanceAfter: 50,
      source: GameTokenTransactionSource.REWARD,
      description: null,
      createdAt: new Date('2026-01-01T00:00:00Z'),
    };

    const embed = UserPanelHistoryViewFactory.buildTokenHistoryEmbed({
      transactions: [tx],
      currentPage: 1,
      totalPages: 1,
      totalCount: 1,
      pageSize: 10,
    });

    expect(embed.description).toContain('獎勵');
    expect(embed.description).toContain('+10');
    expect(embed.footer).toContain('第 1/1 頁（共 1 筆）');
  });
});
