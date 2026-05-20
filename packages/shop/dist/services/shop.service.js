import pino from 'pino';
export const ShopPageHelper = {
    isEmpty(page) {
        return page.products.length === 0;
    },
    hasPreviousPage(page) {
        return page.currentPage > 1;
    },
    hasNextPage(page) {
        return page.currentPage < page.totalPages;
    },
    formatPageIndicator(page) {
        if (page.totalPages <= 1) {
            return `共 ${page.products.length} 個商品`;
        }
        return `第 ${page.currentPage} / ${page.totalPages} 頁`;
    },
};
export const PAGE_SIZE = 5;
export class ShopService {
    productRepository;
    log;
    constructor(productRepository, logger) {
        this.productRepository = productRepository;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async getShopPage(guildId, page) {
        this.log.debug({ guildId, page, pageSize: PAGE_SIZE }, 'Getting shop page');
        const totalCount = await this.productRepository.countByGuildId(guildId);
        const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
        // Ensure page is within valid range (0-based internally)
        const validPage = Math.max(0, Math.min(page, totalPages - 1));
        const products = await this.productRepository.findByGuildIdPaginated(guildId, validPage, PAGE_SIZE);
        this.log.debug({ validPage, productCount: products.length, totalPages }, 'Shop page result');
        return { products, currentPage: validPage + 1, totalPages };
    }
    async searchProducts(guildId, keyword, page) {
        if (!keyword || keyword.trim().length === 0) {
            this.log.debug({ guildId }, 'Search called with empty keyword');
            return { products: [], currentPage: 1, totalPages: 0 };
        }
        this.log.debug({ guildId, keyword, page, pageSize: PAGE_SIZE }, 'Searching products');
        const totalCount = await this.productRepository.countByGuildIdAndNameContaining(guildId, keyword);
        const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
        const validPage = Math.max(0, Math.min(page, totalPages - 1));
        const products = await this.productRepository.findByGuildIdAndNameContaining(guildId, keyword, validPage, PAGE_SIZE);
        this.log.debug({ validPage, productCount: products.length, totalPages }, 'Search result');
        return { products, currentPage: validPage + 1, totalPages };
    }
    async getProductCount(guildId) {
        return this.productRepository.countByGuildId(guildId);
    }
    async hasProducts(guildId) {
        const count = await this.productRepository.countByGuildId(guildId);
        return count > 0;
    }
}
//# sourceMappingURL=shop.service.js.map