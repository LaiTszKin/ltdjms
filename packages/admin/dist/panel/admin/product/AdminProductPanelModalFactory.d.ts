import type { Product } from '@ltdjms/shop';
/**
 * Product-specific modal factory.
 * Matches Java AdminProductPanelModalFactory.
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
