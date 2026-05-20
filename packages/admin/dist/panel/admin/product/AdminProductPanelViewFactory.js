import { ZhTwStrings } from '../../../i18n/zh-TW.js';
/**
 * Product-specific embed view factory.
 * Matches Java AdminProductPanelViewFactory.
 */
export class AdminProductPanelViewFactory {
    /**
     * Builds a product list embed.
     */
    buildProductListEmbed(products, page, totalPages) {
        const fields = products.map((p) => ({
            name: p.name,
            value: `價格：${p.currencyPrice ?? 'N/A'} | 庫存：${p.description ?? '無描述'}`,
            inline: false,
        }));
        return {
            title: ZhTwStrings.productListTitle,
            description: products.length === 0
                ? ZhTwStrings.productListEmpty
                : ZhTwStrings.productPageIndicator
                    .replace('{current}', String(page))
                    .replace('{total}', String(totalPages))
                    .replace('{count}', String(products.length)),
            fields,
            color: 0x2c3e50,
        };
    }
    /**
     * Builds a product detail embed.
     */
    buildProductDetailEmbed(product, codeStats) {
        const codeInfo = codeStats
            ? `兌換碼：${codeStats.totalCount} 個（${codeStats.unusedCount} 可用）`
            : '';
        return {
            title: ZhTwStrings.productDetailTitle.replace('{name}', product.name),
            description: [
                `**描述：** ${product.description ?? '無'}`,
                `**貨幣價格：** ${product.currencyPrice ?? '無'}`,
                `**法幣價格：** ${product.fiatPriceTwd ? `NT$${product.fiatPriceTwd}` : '無'}`,
                codeInfo,
            ].join('\n'),
            fields: [],
            color: 0x2c3e50,
        };
    }
    /**
     * Builds a product code list embed.
     */
    buildProductCodeListEmbed(codes, productName, _page) {
        const codeLines = codes.map((c) => {
            const status = c.redeemed ? ZhTwStrings.productCodeRedeemed : ZhTwStrings.productCodeAvailable;
            return `\`${c.code}\` — ${status}`;
        });
        return {
            title: ZhTwStrings.productCodesTitle.replace('{name}', productName),
            description: codeLines.length > 0 ? codeLines.join('\n') : '暫無兌換碼',
            fields: [],
            color: 0x8e44ad,
        };
    }
}
//# sourceMappingURL=AdminProductPanelViewFactory.js.map