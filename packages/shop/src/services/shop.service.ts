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

export const PAGE_SIZE = 5;

export function formatShopPageFooter(
  currentPage: number,
  totalPages: number,
  productCount: number,
): string {
  if (totalPages <= 1) {
    return `共 ${productCount} 個商品`;
  }
  return `第 ${currentPage} / ${totalPages} 頁`;
}

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
      return formatShopPageFooter(currentPage, totalPages, products.length);
    },
  };
}

type ProductRepositoryPort = {
  countByGuildId(guildId: number): Promise<number>;
  findByGuildIdPaginated(guildId: number, page: number, size: number): Promise<Product[]>;
  countByGuildIdAndNameContaining(guildId: number, keyword: string): Promise<number>;
  findByGuildIdAndNameContaining(
    guildId: number,
    keyword: string,
    page: number,
    size: number,
  ): Promise<Product[]>;
};

export class ShopService {
  private readonly log: pino.Logger;

  constructor(
    private readonly productRepository: ProductRepositoryPort,
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
    return this.fetchPage(
      () => this.productRepository.countByGuildId(guildId),
      (validPage) => this.productRepository.findByGuildIdPaginated(guildId, validPage, pageSize),
      pageIndex,
      pageSize,
      'Shop page result',
    );
  }

  async searchProducts(guildId: number, keyword: string, pageIndex: number): Promise<ShopPage> {
    if (!keyword || keyword.trim().length === 0) {
      this.log.debug({ guildId }, 'Search called with empty keyword');
      return createShopPage([], 1, 0);
    }

    this.log.debug({ guildId, keyword, pageIndex, pageSize: PAGE_SIZE }, 'Searching products');
    return this.fetchPage(
      () => this.productRepository.countByGuildIdAndNameContaining(guildId, keyword),
      (validPage) =>
        this.productRepository.findByGuildIdAndNameContaining(
          guildId,
          keyword,
          validPage,
          PAGE_SIZE,
        ),
      pageIndex,
      PAGE_SIZE,
      'Search result',
    );
  }

  async getProductCount(guildId: number): Promise<number> {
    return this.productRepository.countByGuildId(guildId);
  }

  async hasProducts(guildId: number): Promise<boolean> {
    return (await this.productRepository.countByGuildId(guildId)) > 0;
  }

  private async fetchPage(
    countFn: () => Promise<number>,
    fetchFn: (validPage: number) => Promise<Product[]>,
    pageIndex: number,
    pageSize: number,
    logLabel: string,
  ): Promise<ShopPage> {
    const totalCount = await countFn();
    const totalPages = totalCount === 0 ? 0 : Math.ceil(totalCount / pageSize);
    const validPage = totalPages === 0 ? 0 : Math.max(0, Math.min(pageIndex, totalPages - 1));
    const products = await fetchFn(validPage);

    this.log.debug({ validPage, productCount: products.length, totalPages }, logLabel);
    return createShopPage(products, validPage + 1, totalPages);
  }
}
