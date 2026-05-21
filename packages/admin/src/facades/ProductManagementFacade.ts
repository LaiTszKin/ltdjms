import { type DomainEventPublisher, OperationType } from '@ltdjms/shared';
import type {
  ShopService,
  ProductRepository,
  RedemptionCodeRepository,
  RedemptionCodeGenerator,
  Product,
  RedemptionCode,
  CodeStats,
  ShopPage,
  ProductChangedEvent,
  RedemptionCodesGeneratedEvent,
  createRedemptionCode,
} from '@ltdjms/shop';

/**
 * Facade that wraps ShopService, ProductRepository, RedemptionCodeRepository,
 * and RedemptionCodeGenerator for product management operations.
 * Publishes domain events on successful mutations.
 */
export class ProductManagementFacade {
  constructor(
    private readonly shopService: ShopService,
    private readonly productRepository: ProductRepository,
    private readonly redemptionCodeRepo: RedemptionCodeRepository,
    private readonly codeGenerator: RedemptionCodeGenerator,
    private readonly eventPublisher: DomainEventPublisher,
  ) {}

  // ================================================================
  // Shop / Product Listing
  // ================================================================

  /**
   * Gets a paginated page of products for a guild.
   */
  async getShopPage(guildId: number, page: number): Promise<ShopPage> {
    return this.shopService.getShopPage(guildId, page);
  }

  // ================================================================
  // Product CRUD
  // ================================================================

  /**
   * Finds a product by its ID.
   */
  async findProductById(productId: number): Promise<Product | null> {
    return this.productRepository.findById(productId);
  }

  /**
   * Creates a new product and publishes ProductChangedEvent.
   */
  async createProduct(
    data: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>,
    guildId: string,
  ): Promise<Product> {
    const product = await this.productRepository.create(data);

    this.eventPublisher.publish({
      eventType: 'product_changed',
      guildId,
      productId: product.id!,
      operationType: OperationType.CREATED,
    } as ProductChangedEvent);

    return product;
  }

  /**
   * Updates an existing product and publishes ProductChangedEvent.
   * Returns null if the product was not found.
   */
  async updateProduct(
    productId: number,
    data: Partial<Omit<Product, 'id' | 'createdAt' | 'updatedAt'>>,
    guildId: string,
  ): Promise<Product | null> {
    const product = await this.productRepository.update(productId, data);

    if (product) {
      this.eventPublisher.publish({
        eventType: 'product_changed',
        guildId,
        productId,
        operationType: OperationType.UPDATED,
      } as ProductChangedEvent);
    }

    return product;
  }

  /**
   * Deletes a product and publishes ProductChangedEvent.
   * Returns false if the product was not found.
   */
  async deleteProduct(productId: number, guildId: string): Promise<boolean> {
    const deleted = await this.productRepository.delete(productId);

    if (deleted) {
      this.eventPublisher.publish({
        eventType: 'product_changed',
        guildId,
        productId,
        operationType: OperationType.DELETED,
      } as ProductChangedEvent);
    }

    return deleted;
  }

  // ================================================================
  // Redemption Codes
  // ================================================================

  /**
   * Generates the specified number of redemption code strings.
   */
  generateCodes(count: number): string[] {
    const codes: string[] = [];
    for (let i = 0; i < count; i++) {
      codes.push(this.codeGenerator.generate());
    }
    return codes;
  }

  /**
   * Saves multiple redemption codes and publishes RedemptionCodesGeneratedEvent.
   */
  async saveCodes(
    codes: RedemptionCode[],
    guildId: string,
    productId: number,
    count: number,
  ): Promise<RedemptionCode[]> {
    const saved = await this.redemptionCodeRepo.saveAll(codes);

    this.eventPublisher.publish({
      eventType: 'redemption_codes_generated',
      guildId,
      productId,
      count,
    } as RedemptionCodesGeneratedEvent);

    return saved;
  }

  /**
   * Gets redemption code statistics for a product.
   */
  async getCodeStatsByProductId(productId: number): Promise<CodeStats> {
    return this.redemptionCodeRepo.getStatsByProductId(productId);
  }
}
