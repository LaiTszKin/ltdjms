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

  // Public helper forwarding to conform to the base adjust method name.
  // Consumers call adjustBalance() — map to BaseAccountRepository.adjust().
  async adjustBalance(guildId: number, userId: number, delta: number): Promise<MemberCurrencyAccount> {
    return this.adjust(guildId, userId, delta);
  }

  async tryAdjustBalance(
    guildId: number,
    userId: number,
    delta: number,
  ): ReturnType<BaseAccountRepository<MemberCurrencyAccount>['tryAdjust']> {
    return this.tryAdjust(guildId, userId, delta);
  }

  async setBalance(
    guildId: number,
    userId: number,
    newBalance: number,
  ): Promise<MemberCurrencyAccount> {
    return this.set(guildId, userId, newBalance);
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
    userId: row.userId as number,
    balance: row.balance as number,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}
