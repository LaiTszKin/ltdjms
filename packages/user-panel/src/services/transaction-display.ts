import { CurrencyTransactionSource, type CurrencyTransaction } from '@ltdjms/economy';
import { GameTokenTransactionSource, type GameTokenTransaction } from '@ltdjms/games';
import type { RedemptionTransactionEntry } from '../facades/MemberInfoFacade.js';

const CURRENCY_SOURCE_LABELS: Record<CurrencyTransactionSource, string> = {
  [CurrencyTransactionSource.ADMIN_ADJUSTMENT]: '管理員調整',
  [CurrencyTransactionSource.DICE_GAME_1_WIN]: '骰子遊戲 1 獎勵',
  [CurrencyTransactionSource.DICE_GAME_2_WIN]: '骰子遊戲 2 獎勵',
  [CurrencyTransactionSource.REDEMPTION_CODE]: '兌換碼獎勵',
  [CurrencyTransactionSource.PRODUCT_REWARD]: '商品獎勵',
  [CurrencyTransactionSource.PRODUCT_PURCHASE]: '商品購買',
  [CurrencyTransactionSource.PRODUCT_PURCHASE_REFUND]: '商品購買退款',
};

const TOKEN_SOURCE_LABELS: Record<GameTokenTransactionSource, string> = {
  [GameTokenTransactionSource.ADMIN_ADJUSTMENT]: '管理員調整',
  [GameTokenTransactionSource.DICE_GAME_1_PLAY]: '骰子遊戲 1 消耗',
  [GameTokenTransactionSource.DICE_GAME_1_REFUND]: '骰子遊戲 1 退款',
  [GameTokenTransactionSource.DICE_GAME_2_PLAY]: '骰子遊戲 2 消耗',
  [GameTokenTransactionSource.DICE_GAME_2_REFUND]: '骰子遊戲 2 退款',
  [GameTokenTransactionSource.GAME_PLAY]: '遊戲消耗',
  [GameTokenTransactionSource.REWARD]: '獎勵',
  [GameTokenTransactionSource.INITIAL]: '初始化',
  [GameTokenTransactionSource.REDEMPTION_CODE]: '兌換碼獎勵',
  [GameTokenTransactionSource.PRODUCT_REWARD]: '商品獎勵',
};

function formatAmount(amount: number): string {
  const formatted = Math.abs(amount).toLocaleString('en-US');
  return amount >= 0 ? `+${formatted}` : `-${formatted}`;
}

function formatBalance(balance: number): string {
  return balance.toLocaleString('en-US');
}

export function getShortTimestamp(createdAt: Date): string {
  const epochSeconds = Math.floor(createdAt.getTime() / 1000);
  return `<t:${epochSeconds}:R>`;
}

export function formatCurrencyTransactionForDisplay(tx: CurrencyTransaction): string {
  const sourceLabel = CURRENCY_SOURCE_LABELS[tx.source];
  const descStr = tx.description?.trim() ? ` - ${tx.description}` : '';
  return `${sourceLabel} | ${formatAmount(tx.amount)} | 餘額: ${formatBalance(tx.balanceAfter)}${descStr}`;
}

export function formatTokenTransactionForDisplay(tx: GameTokenTransaction): string {
  const sourceLabel = TOKEN_SOURCE_LABELS[tx.source];
  const descStr = tx.description?.trim() ? ` - ${tx.description}` : '';
  return `${sourceLabel} | ${formatAmount(tx.amount)} | 餘額: ${formatBalance(tx.balanceAfter)}${descStr}`;
}

function maskRedemptionCode(code: string): string {
  if (code.length <= 8) return code;
  return `${code.slice(0, 4)}****${code.slice(-4)}`;
}

export function formatProductRedemptionForDisplay(entry: RedemptionTransactionEntry): string {
  let line = `**${entry.productName}**`;
  if (entry.rewardAmount != null) {
    line += ` | 貨幣 +${entry.rewardAmount.toLocaleString('en-US')}`;
  } else {
    line += ' | 無自動獎勵';
  }
  line += ` | \`${maskRedemptionCode(entry.code)}\``;
  return line;
}

export interface HistoryPageView {
  readonly currentPage: number;
  readonly totalPages: number;
  readonly totalCount: number;
  readonly pageSize: number;
}

export function hasPreviousPage(page: HistoryPageView): boolean {
  return page.currentPage > 1;
}

export function hasNextPage(page: HistoryPageView): boolean {
  return page.currentPage < page.totalPages;
}
