import { buildCheckMacValue } from '../crypto/ecpay-checkmac.js';
import { ok, err, DomainError } from '@ltdjms/shared';
import pino from 'pino';
const STAGE_ENDPOINT = 'https://payment-stage.ecpay.com.tw/Cashier/QueryTradeInfo/V5';
const PROD_ENDPOINT = 'https://payment.ecpay.com.tw/Cashier/QueryTradeInfo/V5';
export class EcpayTradeQueryService {
    config;
    log;
    constructor(config, logger) {
        this.config = config;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async queryTrade(orderNumber) {
        if (!orderNumber || orderNumber.trim().length === 0) {
            return err(DomainError.invalidInput('訂單編號不可為空'));
        }
        const merchantId = this.config.getEcpayMerchantId().trim();
        const hashKey = this.config.getEcpayHashKey().trim();
        const hashIv = this.config.getEcpayHashIv().trim();
        if (!merchantId || !hashKey || !hashIv) {
            return err(DomainError.invalidInput('綠界金流尚未完成設定（MerchantID/HashKey/HashIV）'));
        }
        try {
            const timeStamp = Math.floor(Date.now() / 1000).toString();
            const params = {
                MerchantID: merchantId,
                MerchantTradeNo: orderNumber.trim(),
                TimeStamp: timeStamp,
            };
            params.CheckMacValue = buildCheckMacValue(params, hashKey, hashIv);
            const formBody = this.buildFormBody(params);
            const endpoint = this.config.getEcpayStageMode() ? STAGE_ENDPOINT : PROD_ENDPOINT;
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                },
                body: formBody,
                signal: AbortSignal.timeout(15000),
            });
            if (!response.ok) {
                return err(DomainError.unexpectedFailure(`綠界查單失敗（HTTP ${response.status}）`));
            }
            const body = await response.text();
            const parsed = this.parseFormBody(body);
            const tradeStatus = this.textOrNull(parsed.get('TradeStatus') ?? null);
            const tradeNo = this.textOrNull(parsed.get('TradeNo') ?? null);
            const message = this.firstNonBlank(parsed.get('RtnMsg'), parsed.get('TradeMsg'), parsed.get('PaymentType'));
            const tradeAmtStr = parsed.get('TradeAmt');
            const tradeAmt = tradeAmtStr ? this.parseLongOrDefault(tradeAmtStr, -1) : -1;
            const paid = tradeStatus === '1';
            return ok({
                orderNumber,
                paid,
                tradeStatus,
                tradeNo,
                tradeAmount: tradeAmt,
                message,
            });
        }
        catch (e) {
            if (e.name === 'AbortError') {
                return err(DomainError.unexpectedFailure('綠界查單逾時'));
            }
            this.log.warn({ error: e, orderNumber }, 'Failed to query ECPay trade info');
            return err(DomainError.unexpectedFailure('綠界查單失敗'));
        }
    }
    buildFormBody(params) {
        const parts = [];
        for (const [key, value] of Object.entries(params)) {
            parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
        }
        return parts.join('&');
    }
    parseFormBody(body) {
        const values = new Map();
        if (!body)
            return values;
        for (const pair of body.split('&')) {
            if (!pair)
                continue;
            const idx = pair.indexOf('=');
            const key = idx >= 0 ? decodeURIComponent(pair.substring(0, idx)) : decodeURIComponent(pair);
            const value = idx >= 0 ? decodeURIComponent(pair.substring(idx + 1)) : '';
            values.set(key, value);
        }
        return values;
    }
    parseLongOrDefault(value, fallback) {
        if (!value || value.trim().length === 0)
            return fallback;
        try {
            return parseInt(value.trim(), 10);
        }
        catch {
            return fallback;
        }
    }
    textOrNull(value) {
        if (!value || value.trim().length === 0)
            return null;
        return value.trim();
    }
    firstNonBlank(...values) {
        for (const v of values) {
            if (v && v.trim().length > 0)
                return v.trim();
        }
        return null;
    }
}
//# sourceMappingURL=ecpay-trade-query.service.js.map