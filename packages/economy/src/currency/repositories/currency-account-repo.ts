import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { memberCurrencyAccount } from '../../domain/schema.js';
import type { MemberCurrencyAccount } from '../../domain/types.js';
import { DomainError } from '@ltdjms/shared';
import { BaseAccountRepository } from '../../common/base-account-repo.js';

/**
 * Repository for member currency account operations using Drizzle ORM.
 * Matches Java JooqMemberCurrencyAccountRepository behavior.
 */
export class CurrencyAccountRepository extends BaseAccountRepository<MemberCurrencyAccount> {
  constructor(db: NodePgDatabase) {
    super(db, {
      table: memberCurrencyAccount,
      balanceFieldName: 'balance',
      updatedAtFieldName: 'updatedAt',
      defaultValues: { balance: 0 },
      mapToDomain,
      newInsufficientError: (msg) => new InsufficientBalanceError(msg),
      domainInsufficientError: (msg) => DomainError.insufficientBalance(msg),
    });
  }

  async tryAdjustBalance(
    guildId: number,
    userId: string,
    delta: number,
  ): ReturnType<BaseAccountRepository<MemberCurrencyAccount>['tryAdjust']> {
    return this.tryAdjust(guildId, userId, delta);
  }
}

/**
 * Error thrown when a balance adjustment would result in a negative balance.
 */
export class InsufficientBalanceError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InsufficientBalanceError';
  }
}

function mapToDomain(row: Record<string, unknown>): MemberCurrencyAccount {
  return {
    guildId: row.guildId as number,
    userId: String(row.userId),
    balance: row.balance as number,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}
