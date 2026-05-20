import type { Product } from '@ltdjms/shop';
/**
 * Product-specific modal factory.
 * Matches Java AdminProductPanelModalFactory.
 *
 * RESPONSIBILITY: This factory handles product-specific modals (create/edit
 * product, generate codes). Generic/shared admin panel modals (balance, tokens,
 * games, escort pricing/catalog) are in AdminPanelModalFactory under
 * panel/admin/views/.
 *
 * @see AdminPanelModalFactory — generic admin panel modals
 */
export declare class AdminProductPanelModalFactory {
    /**
     * Builds a create product modal.
     */
    buildCreateProductModal(): {
        title: string;
        fields: {
            label: string;
            placeholder: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds an edit product modal pre-filled with current values.
     */
    buildEditProductModal(product: Product): {
        title: string;
        fields: {
            label: string;
            value: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds a generate codes modal.
     */
    buildGenerateCodesModal(): {
        title: string;
        fields: {
            label: string;
            placeholder: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
}
