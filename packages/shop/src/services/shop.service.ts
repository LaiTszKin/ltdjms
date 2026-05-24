import { type Product } from '../domain/product-types.js';
import pino from 'pino';

export interface ShopPage {
  products: Product[];
  currentPage: number;
  totalPages: number;
  isEmpty(): boolean;
  hasPreviousPage(): boolean;
  hasNextPage(): boolean;
  formatPageIndicator(): string;
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
  hasPreviousPage(page: ShopPage): boolean {
    return page.currentPage > 1;
  },
  hasNextPage(page: ShopPage): boolean {
    return page.currentPage < page.totalPages;
  },
};

export const PAGE_SIZE = 5;

function createShopPage(products: Product[], currentPage: number, totalPages: number): ShopPage {
  return {
    products,
    currentPage,
    totalPages,
    isEmpty() {
      return products.length === 0;
    },
    hasPreviousPage() {
      return currentPage > 1;
    },
    hasNextPage() {
      return currentPage < totalPages;
    },
    formatPageIndicator() {
      return ShopPageHelper.formatPageIndicator(this);
    },
  };
}

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

  async getShopPage(guildId: number, pageIndex: number): Promise<ShopPage> {
    return this.getShopPageWithSize(guildId, pageIndex, PAGE_SIZE);
  }

  async getShopPageWithSize(
    guildId: number,
    pageIndex: number,
    pageSize: number,
  ): Promise<ShopPage> {
    this.log.debug({ guildId, pageIndex, pageSize }, 'Getting shop page');

    const totalCount = await this.productRepository.countByGuildId(guildId);
    const totalPages = totalCount === 0 ? 0 : Math.ceil(totalCount / pageSize);

    const validPage =
      totalPages === 0 ? 0 : Math.max(0, Math.min(pageIndex, totalPages - 1));
    const products = await this.productRepository.findByGuildIdPaginated(
      guildId,
      validPage,
      pageSize,
    );

    this.log.debug({ validPage, productCount: products.length, totalPages }, 'Shop page result');

    return createShopPage(products, validPage + 1, totalPages);
  }

  async searchProducts(guildId: number, keyword: string, pageIndex: number): Promise<ShopPage> {
    if (!keyword || keyword.trim().length === 0) {
      this.log.debug({ guildId }, 'Search called with empty keyword');
      return createShopPage([], 1, 0);
    }

    this.log.debug({ guildId, keyword, pageIndex, pageSize: PAGE_SIZE }, 'Searching products');

    const totalCount = await this.productRepository.countByGuildIdAndNameContaining(
      guildId,
      keyword,
    );
    const totalPages = totalCount === 0 ? 0 : Math.ceil(totalCount / PAGE_SIZE);

    const validPage =
      totalPages === 0 ? 0 : Math.max(0, Math.min(pageIndex, totalPages - 1));
    const products = await this.productRepository.findByGuildIdAndNameContaining(
      guildId,
      keyword,
      validPage,
      PAGE_SIZE,
    );

    this.log.debug({ validPage, productCount: products.length, totalPages }, 'Search result');

    return createShopPage(products, validPage + 1, totalPages);
  }

  async getProductCount(guildId: number): Promise<number> {
    return this.productRepository.countByGuildId(guildId);
  }

  async hasProducts(guildId: number): Promise<boolean> {
    return (await this.productRepository.countByGuildId(guildId)) > 0;
  }
}
