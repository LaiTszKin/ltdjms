import type { Product } from '@ltdjms/shop';
/**
 * Product-specific embed view factory.
 * Matches Java AdminProductPanelViewFactory.
 *
 * RESPONSIBILITY: This factory handles product-specific views (product list,
 * product detail, code list). Generic/shared admin panel views (main menu,
 * balance, tokens, games, AI config, dispatch) are in AdminPanelViewFactory
 * under panel/admin/views/.
 *
 * @see AdminPanelViewFactory — generic admin panel views
 */
export declare class AdminProductPanelViewFactory {
    /**
     * Builds a product list embed.
     */
    buildProductListEmbed(products: Product[], page: number, totalPages: number): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
    };
    /**
     * Builds a product detail embed.
     */
    buildProductDetailEmbed(product: Product, codeStats?: {
        totalCount: number;
        unusedCount: number;
    }): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
    };
    /**
     * Builds a product code list embed.
     */
    buildProductCodeListEmbed(codes: {
        code: string;
        redeemed: boolean;
    }[], productName: string, _page: number): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
    };
}
