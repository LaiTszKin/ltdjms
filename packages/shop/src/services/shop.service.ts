import { type Product } from '../domain/product-types.js';
import pino from 'pino';

export interface ShopPage {
  products: Product[];
  currentPage: number;
  totalPages: number;
}

export const ShopPageHelper = {
  isEmpty(page: ShopPage): boolean {
    return page.products.length === 0;
  },
  formatPageIndicator(page: ShopPage): string {
    if (page.totalPages <= 1) {
      return `共 ${page.products.length} 個商品`;
    }
    return `第 ${page.currentPage} / ${page.totalPages} 頁`;
  },
};

export const PAGE_SIZE = 5;

export class ShopService {
  private readonly log: pino.Logger;

  constructor(
    private readonly productRepository: {
      countByGuildId(guildId: number): Promise<number>;
      findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]>;
      countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number>;
      findByGuildIdAndNameContaining(
        guildId: number,
        keyword: string,
        page: number,
        size: number,
      ): Promise<Product[]>;
    },
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async getShopPage(guildId: number, page: number): Promise<ShopPage> {
    return this.getShopPageWithSize(guildId, page, PAGE_SIZE);
  }

  /**
   * Like getShopPage but accepts an explicit page size override.
   * Used by ShopCommandHandler.showBuySelection to load all products at once.
   */
  async getShopPageWithSize(guildId: number, page: number, pageSize: number): Promise<ShopPage> {
    this.log.debug({ guildId, page, pageSize }, 'Getting shop page');

    const totalCount = await this.productRepository.countByGuildId(guildId);
    const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));

    // Ensure page is within valid range (0-based internally)
    const validPage = Math.max(0, Math.min(page, totalPages - 1));
    const products = await this.productRepository.findByGuildIdPaginated(
      guildId,
      validPage,
      pageSize,
    );

    this.log.debug({ validPage, productCount: products.length, totalPages }, 'Shop page result');

    return { products, currentPage: validPage + 1, totalPages };
  }

  async searchProducts(guildId: number, keyword: string, page: number): Promise<ShopPage> {
    if (!keyword || keyword.trim().length === 0) {
      this.log.debug({ guildId }, 'Search called with empty keyword');
      return { products: [], currentPage: 1, totalPages: 0 };
    }

    this.log.debug({ guildId, keyword, page, pageSize: PAGE_SIZE }, 'Searching products');

    const totalCount = await this.productRepository.countByGuildIdAndNameContaining(
      guildId,
      keyword,
    );
    const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

    const validPage = Math.max(0, Math.min(page, totalPages - 1));
    const products = await this.productRepository.findByGuildIdAndNameContaining(
      guildId,
      keyword,
      validPage,
      PAGE_SIZE,
    );

    this.log.debug({ validPage, productCount: products.length, totalPages }, 'Search result');

    return { products, currentPage: validPage + 1, totalPages };
  }
}
