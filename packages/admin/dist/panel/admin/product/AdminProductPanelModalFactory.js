import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Product-specific modal factory.
 * Matches Java AdminProductPanelModalFactory.
 */
export class AdminProductPanelModalFactory {
    /**
     * Builds a create product modal.
     */
    buildCreateProductModal() {
        return {
            title: ZhTwStrings.productCreateModalTitle,
            fields: [
                { label: '名稱', placeholder: '請輸入產品名稱', minLength: 1, maxLength: 100, required: true },
                { label: '描述', placeholder: '請輸入產品描述', minLength: 0, maxLength: 1000, required: false },
                { label: '貨幣價格', placeholder: '請輸入貨幣價格', minLength: 0, maxLength: 20, required: true },
                { label: '法幣價格 (TWD)', placeholder: '請輸入新台幣價格', minLength: 0, maxLength: 20, required: false },
                { label: '庫存', placeholder: '請輸入庫存數量', minLength: 1, maxLength: 10, required: false },
            ],
        };
    }
    /**
     * Builds an edit product modal pre-filled with current values.
     */
    buildEditProductModal(product) {
        return {
            title: `${ZhTwStrings.productEditBtn} - ${product.name}`,
            fields: [
                { label: '名稱', value: product.name, minLength: 1, maxLength: 100, required: true },
                { label: '描述', value: product.description ?? '', minLength: 0, maxLength: 1000, required: false },
                { label: '貨幣價格', value: product.currencyPrice?.toString() ?? '0', minLength: 1, maxLength: 20, required: true },
                { label: '法幣價格 (TWD)', value: product.fiatPriceTwd?.toString() ?? '', minLength: 0, maxLength: 20, required: false },
            ],
        };
    }
    /**
     * Builds a generate codes modal.
     */
    buildGenerateCodesModal() {
        return {
            title: ZhTwStrings.generateCodesModalTitle,
            fields: [
                { label: '數量', placeholder: '1-100', minLength: 1, maxLength: 3, required: true },
                { label: '備註', placeholder: '選填', minLength: 0, maxLength: 100, required: false },
                { label: '有效天數', placeholder: '留空為永久有效', minLength: 0, maxLength: 5, required: false },
            ],
        };
    }
}
//# sourceMappingURL=AdminProductPanelModalFactory.js.map