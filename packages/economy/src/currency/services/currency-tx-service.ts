import { CurrencyTransactionRepository } from '../repositories/currency-tx-repo.js';
import type { CurrencyTransaction } from '../../domain/types.js';
import { CurrencyTransactionSource } from '../../domain/types.js';
import { BaseTransactionService } from '../../common/base-tx-service.js';

/**
 * Service for querying and recording currency transaction history.
 * Matches Java CurrencyTransactionService behavior.
 */
export class CurrencyTransactionService extends BaseTransactionService<CurrencyTransaction, CurrencyTransactionSource> {
  static readonly DEFAULT_PAGE_SIZE = BaseTransactionService.DEFAULT_PAGE_SIZE;

  constructor(transactionRepository: CurrencyTransactionRepository) {
    super(transactionRepository);
  }
}
