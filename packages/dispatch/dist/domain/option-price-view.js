/** Formatter for OptionPriceView display in Discord. */
export function optionPriceToDisplayLine(view) {
    const suffix = view.overridden ? '（已覆蓋）' : '（預設）';
    return ('`' +
        view.optionCode +
        '` ' +
        view.option.type +
        '｜' +
        view.option.level +
        '｜' +
        view.option.target +
        '｜NT$' +
        view.effectivePriceTwd.toLocaleString() +
        ' ' +
        suffix);
}
//# sourceMappingURL=option-price-view.js.map