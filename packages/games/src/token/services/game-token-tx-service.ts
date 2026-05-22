import { TokenTransactionRepository } from '../repositories/token-tx-repo.js';
import type { GameTokenTransaction } from '../../domain/types.js';
import { GameTokenTransactionSource } from '../../domain/types.js';
import { BaseTransactionService } from '@ltdjms/economy';

/**
 * Service for querying and recording game token transaction history.
 * Matches Java GameTokenTransactionService behavior.
 */
export class GameTokenTransactionService extends BaseTransactionService<
  GameTokenTransaction,
  GameTokenTransactionSource
> {
  constructor(transactionRepository: TokenTransactionRepository) {
    super(transactionRepository);
  }
}
