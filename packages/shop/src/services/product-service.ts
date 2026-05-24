import { type Result, ok, err, DomainError } from '@ltdjms/shared';
import type { DomainEventPublisher } from '@ltdjms/shared';
import { type Product, type ProductRepository } from '../domain/product-types.js';
import { OperationType } from '@ltdjms/shared';
import { type ProductChangedEvent } from '../events/index.js';
import pino from 'pino';

export class ProductService {
  private readonly log: pino.Logger;

  constructor(
    private readonly repository: ProductRepository,
    private readonly eventPublisher: DomainEventPublisher,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async findById(id: number): Promise<Product | null> {
    return this.repository.findById(id);
  }

  async getProduct(productId: number): Promise<Product | null> {
    return this.repository.findById(productId);
  }

  async getProductsForPurchase(guildId: number): Promise<Product[]> {
    return this.repository.findByGuildIdWithCurrencyPrice(guildId);
  }

  async getFiatOnlyProducts(guildId: number): Promise<Product[]> {
    return this.repository.findFiatOnlyByGuildId(guildId);
  }

  async getAllPurchasableProducts(guildId: number): Promise<Product[]> {
    const currencyProducts = await this.repository.findByGuildIdWithCurrencyPrice(guildId);
    const fiatProducts = await this.repository.findFiatOnlyByGuildId(guildId);
    return [...currencyProducts, ...fiatProducts];
  }

  async countByGuildId(guildId: number): Promise<number> {
    return this.repository.countByGuildId(guildId);
  }

  async findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]> {
    return this.repository.findByGuildIdPaginated(guildId, page, size);
  }

  async countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number> {
    return this.repository.countByGuildIdAndNameContaining(guildId, keyword);
  }

  async findByGuildIdAndNameContaining(
    guildId: number,
    keyword: string,
    page: number,
    size: number,
  ): Promise<Product[]> {
    return this.repository.findByGuildIdAndNameContaining(guildId, keyword, page, size);
  }

  async create(
    data: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>,
  ): Promise<Result<Product, DomainError>> {
    if (!data.name || data.name.trim().length === 0) {
      return err(DomainError.invalidInput('商品名稱不可為空'));
    }

    try {
      const product = await this.repository.create(data);
      this.publishProductChanged(product, OperationType.CREATED);
      this.log.info({ productId: product.id, guildId: product.guildId }, 'Product created');
      return ok(product);
    } catch (e) {
      this.log.error({ guildId: data.guildId, error: e }, 'Failed to create product');
      return err(DomainError.persistenceFailure('建立商品失敗'));
    }
  }

  async update(
    id: number,
    data: Partial<Omit<Product, 'id' | 'createdAt' | 'updatedAt'>>,
  ): Promise<Result<Product, DomainError>> {
    try {
      const existing = await this.repository.findById(id);
      if (!existing) {
        return err(DomainError.invalidInput('找不到該商品'));
      }

      const product = await this.repository.update(id, data);
      if (!product) {
        return err(DomainError.persistenceFailure('更新商品失敗'));
      }

      this.publishProductChanged(product, OperationType.UPDATED);
      this.log.info({ productId: id }, 'Product updated');
      return ok(product);
    } catch (e) {
      this.log.error({ productId: id, error: e }, 'Failed to update product');
      return err(DomainError.persistenceFailure('更新商品失敗'));
    }
  }

  async delete(id: number): Promise<Result<boolean, DomainError>> {
    try {
      const existing = await this.repository.findById(id);
      if (!existing) {
        return err(DomainError.invalidInput('找不到該商品'));
      }

      const deleted = await this.repository.delete(id);
      if (deleted) {
        this.publishProductChanged({ ...existing, id }, OperationType.DELETED);
        this.log.info({ productId: id }, 'Product deleted');
      }
      return ok(deleted);
    } catch (e) {
      this.log.error({ productId: id, error: e }, 'Failed to delete product');
      return err(DomainError.persistenceFailure('刪除商品失敗'));
    }
  }

  private publishProductChanged(product: Product, operationType: OperationType): void {
    const event: ProductChangedEvent = {
      eventType: 'product_changed',
      guildId: String(product.guildId),
      productId: product.id!,
      operationType,
    };
    this.eventPublisher.publish(event);
  }
}
