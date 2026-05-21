import { NodePgDatabase } from 'drizzle-orm/node-postgres';
import { gameTokenAccount } from '../../domain/schema.js';
import type { GameTokenAccount } from '../../domain/types.js';
import { DomainError } from '@ltdjms/shared';
import { BaseAccountRepository } from '../../common/base-account-repo.js';

/**
 * Repository for game token account operations using Drizzle ORM.
 * Matches Java GameTokenAccountRepository behavior.
 */
export class TokenAccountRepository extends BaseAccountRepository<GameTokenAccount> {
  constructor(db: NodePgDatabase) {
    super(db, {
      table: gameTokenAccount,
      balanceFieldName: 'tokens',
      updatedAtFieldName: 'updatedAt',
      defaultValues: { tokens: 0 },
      mapToDomain,
      newInsufficientError: (msg) => new InsufficientTokensError(msg),
      domainInsufficientError: (msg) => DomainError.insufficientTokens(msg),
    });
  }

  async adjustTokens(guildId: number, userId: number, delta: number): Promise<GameTokenAccount> {
    return this.adjust(guildId, userId, delta);
  }

  async tryAdjustTokens(
    guildId: number,
    userId: number,
    delta: number,
  ): ReturnType<BaseAccountRepository<GameTokenAccount>['tryAdjust']> {
    return this.tryAdjust(guildId, userId, delta);
  }

  async setTokens(
    guildId: number,
    userId: number,
    newTokens: number,
  ): Promise<GameTokenAccount> {
    return this.set(guildId, userId, newTokens);
  }
}

/** Error thrown when a token adjustment would result in a negative balance. */
export class InsufficientTokensError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InsufficientTokensError';
  }
}

function mapToDomain(row: Record<string, unknown>): GameTokenAccount {
  return {
    guildId: row.guildId as number,
    userId: row.userId as number,
    tokens: row.tokens as number,
    createdAt: row.createdAt as Date,
    updatedAt: row.updatedAt as Date,
  };
}
