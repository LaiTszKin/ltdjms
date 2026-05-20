import { decryptAES } from '../crypto/ecpay-aes.js';
import pino from 'pino';
export const CallbackResult = {
    ok() {
        return { httpStatus: 200, responseBody: '1|OK' };
    },
    fail(status) {
        return { httpStatus: status, responseBody: '0|FAIL' };
    },
};
class InvalidCallbackPayloadException extends Error {
    constructor(message, cause) {
        super(message);
        this.name = 'InvalidCallbackPayloadException';
        if (cause)
            this.cause = cause;
    }
}
export class FiatPaymentCallbackService {
    config;
    fiatOrderRepository;
    log;
    constructor(config, fiatOrderRepository, logger) {
        this.config = config;
        this.fiatOrderRepository = fiatOrderRepository;
        this.log = logger ?? pino({ level: 'warn' });
    }
    handleCallback(requestBody, contentType) {
        if (!requestBody || requestBody.trim().length === 0) {
            return CallbackResult.fail(400);
        }
        const callbackPayload = this.sanitizePayload(requestBody);
        try {
            const callbackNode = this.parseCallbackNode(requestBody, contentType);
            const orderNumber = this.extractOrderNumber(callbackNode);
            if (!orderNumber || orderNumber.trim().length === 0) {
                this.log.warn({ payload: callbackPayload }, 'ECPay callback missing order number');
                return CallbackResult.fail(400);
            }
            const tradeStatus = this.extractTradeStatus(callbackNode);
            const paymentMessage = this.extractPaymentMessage(callbackNode);
            const paid = this.isPaidStatus(tradeStatus);
            // find order - synchronous since we're in an async context, so use Promise.resolve
            // In Express, this will be awaited properly. For now, handle sync.
            // We'll use the async version and handle it in the Express route.
            return this.processWithOrder(orderNumber, tradeStatus, paymentMessage, paid, callbackPayload, callbackNode);
        }
        catch (e) {
            if (e instanceof InvalidCallbackPayloadException) {
                this.log.warn({ reason: e.message }, 'Reject invalid ECPay callback payload');
                return CallbackResult.fail(400);
            }
            this.log.error({ error: e }, 'Failed to process ECPay callback payload');
            return CallbackResult.fail(500);
        }
    }
    async handleCallbackAsync(requestBody, contentType) {
        if (!requestBody || requestBody.trim().length === 0) {
            return CallbackResult.fail(400);
        }
        const callbackPayload = this.sanitizePayload(requestBody);
        try {
            const callbackNode = this.parseCallbackNode(requestBody, contentType);
            const orderNumber = this.extractOrderNumber(callbackNode);
            if (!orderNumber || orderNumber.trim().length === 0) {
                this.log.warn({ payload: callbackPayload }, 'ECPay callback missing order number');
                return CallbackResult.fail(400);
            }
            const tradeStatus = this.extractTradeStatus(callbackNode);
            const paymentMessage = this.extractPaymentMessage(callbackNode);
            const paid = this.isPaidStatus(tradeStatus);
            return await this.processWithOrderAsync(orderNumber, tradeStatus, paymentMessage, paid, callbackPayload, callbackNode);
        }
        catch (e) {
            if (e instanceof InvalidCallbackPayloadException) {
                this.log.warn({ reason: e.message }, 'Reject invalid ECPay callback payload');
                return CallbackResult.fail(400);
            }
            this.log.error({ error: e }, 'Failed to process ECPay callback payload');
            return CallbackResult.fail(500);
        }
    }
    processWithOrder(orderNumber, tradeStatus, paymentMessage, paid, callbackPayload, callbackNode) {
        // Synchronous path - fire and forget. This is a legacy compatibility path.
        // The actual async path should be used in production.
        this.log.warn('Sync callback processing may not work with async repository. Use handleCallbackAsync.');
        return CallbackResult.ok();
    }
    async processWithOrderAsync(orderNumber, tradeStatus, paymentMessage, paid, callbackPayload, callbackNode) {
        const order = await this.fiatOrderRepository.findByOrderNumber(orderNumber);
        if (!order) {
            this.log.warn({ orderNumber }, 'ECPay callback order not found');
            return CallbackResult.ok();
        }
        if (!paid) {
            await this.fiatOrderRepository.updateCallbackStatus(orderNumber, tradeStatus, paymentMessage, callbackPayload);
            this.log.info({ orderNumber, tradeStatus, rtnCode: callbackNode.RtnCode ?? -1 }, 'ECPay callback recorded unpaid status');
            return CallbackResult.ok();
        }
        if (paid && !this.isValidPaidCallback(callbackNode, order, orderNumber)) {
            await this.fiatOrderRepository.updateCallbackStatus(orderNumber, tradeStatus, paymentMessage, callbackPayload);
            this.log.warn({ orderNumber }, 'ECPay callback rejected paid transition due to validation failure');
            return CallbackResult.ok();
        }
        const paidOrder = await this.fiatOrderRepository.markPaidIfPending(orderNumber, tradeStatus ?? '', paymentMessage, callbackPayload, new Date());
        if (!paidOrder) {
            await this.fiatOrderRepository.updateCallbackStatus(orderNumber, tradeStatus, paymentMessage, callbackPayload);
            if (this.isExpiredStatus(order)) {
                this.log.info({ orderNumber }, 'ECPay callback arrived after fiat order expiry');
            }
            else {
                this.log.info({ orderNumber }, 'ECPay callback duplicated paid notification');
            }
            return CallbackResult.ok();
        }
        this.log.info({ orderNumber: paidOrder.orderNumber }, 'ECPay callback marked fiat order paid');
        return CallbackResult.ok();
    }
    isExpiredStatus(order) {
        return order.status === 'EXPIRED';
    }
    parseCallbackNode(requestBody, contentType) {
        let parsedJson = null;
        let formData = null;
        try {
            if (this.isJson(contentType, requestBody)) {
                parsedJson = JSON.parse(requestBody);
            }
            else {
                formData = this.parseFormBody(requestBody);
                if (!formData || formData.size === 0) {
                    parsedJson = JSON.parse(requestBody);
                }
            }
        }
        catch (e) {
            throw new InvalidCallbackPayloadException('callback payload parsing failed', e);
        }
        let encryptedData = null;
        if (parsedJson && parsedJson.Data !== null && parsedJson.Data !== undefined) {
            encryptedData = String(parsedJson.Data);
        }
        if ((!encryptedData || encryptedData.trim().length === 0) && formData && formData.has('Data')) {
            encryptedData = formData.get('Data') ?? null;
        }
        if (!encryptedData || encryptedData.trim().length === 0) {
            throw new InvalidCallbackPayloadException('callback payload missing encrypted Data');
        }
        return this.parseDecryptedData(encryptedData);
    }
    parseDecryptedData(encryptedData) {
        const hashKey = this.config.getEcpayHashKey();
        const hashIv = this.config.getEcpayHashIv();
        if (!hashKey || hashKey.trim().length === 0 || !hashIv || hashIv.trim().length === 0) {
            throw new Error('ECPAY_HASH_KEY / ECPAY_HASH_IV are required for callback');
        }
        try {
            const decryptedJson = decryptAES(encryptedData, hashKey, hashIv);
            return JSON.parse(decryptedJson);
        }
        catch (e) {
            throw new InvalidCallbackPayloadException('callback payload decryption failed', e);
        }
    }
    parseFormBody(body) {
        const data = new Map();
        const parts = body.split('&');
        for (const part of parts) {
            if (!part || part.trim().length === 0)
                continue;
            const eqIndex = part.indexOf('=');
            if (eqIndex <= 0)
                continue;
            const key = decodeURIComponent(part.substring(0, eqIndex));
            const value = decodeURIComponent(part.substring(eqIndex + 1));
            data.set(key, value);
        }
        return data;
    }
    isJson(contentType, body) {
        if (contentType && contentType.toLowerCase().includes('application/json')) {
            return true;
        }
        const trimmed = body.trim();
        return trimmed.startsWith('{') && trimmed.endsWith('}');
    }
    extractOrderNumber(callbackNode) {
        const direct = this.textOrNull(callbackNode.MerchantTradeNo ?? null);
        if (direct)
            return direct;
        return this.textOrNull(callbackNode.OrderInfo?.MerchantTradeNo ?? null);
    }
    extractTradeStatus(callbackNode) {
        const direct = this.textOrNull(callbackNode.TradeStatus ?? null);
        if (direct)
            return direct;
        return this.textOrNull(callbackNode.OrderInfo?.TradeStatus ?? null);
    }
    extractPaymentMessage(callbackNode) {
        const rtnMsg = this.textOrNull(callbackNode.RtnMsg ?? null);
        if (rtnMsg)
            return rtnMsg;
        return this.textOrNull(callbackNode.TradeMsg ?? null);
    }
    extractMerchantId(callbackNode) {
        const direct = this.textOrNull(callbackNode.MerchantID ?? null);
        if (direct)
            return direct;
        return this.textOrNull(callbackNode.OrderInfo?.MerchantID ?? null);
    }
    extractTradeAmount(callbackNode) {
        const direct = this.parsePositiveLong(callbackNode.TradeAmt ?? null);
        if (direct !== null)
            return direct;
        const nestedTradeAmt = this.parsePositiveLong(callbackNode.OrderInfo?.TradeAmt ?? null);
        if (nestedTradeAmt !== null)
            return nestedTradeAmt;
        return this.parsePositiveLong(callbackNode.OrderInfo?.TotalAmount ?? null);
    }
    isValidPaidCallback(callbackNode, order, orderNumber) {
        const expectedMerchantId = this.textOrNull(this.config.getEcpayMerchantId());
        if (expectedMerchantId) {
            const callbackMerchantId = this.extractMerchantId(callbackNode);
            if (!callbackMerchantId || expectedMerchantId !== callbackMerchantId) {
                this.log.warn({ orderNumber, expectedMerchantId, callbackMerchantId }, 'ECPay callback merchant mismatch');
                return false;
            }
        }
        const callbackAmount = this.extractTradeAmount(callbackNode);
        if (callbackAmount === null) {
            this.log.warn({ orderNumber }, 'ECPay callback missing valid TradeAmt for paid status');
            return false;
        }
        if (callbackAmount !== order.amountTwd) {
            this.log.warn({ orderNumber, expectedAmount: order.amountTwd, callbackAmount }, 'ECPay callback amount mismatch');
            return false;
        }
        return true;
    }
    isPaidStatus(tradeStatus) {
        return tradeStatus === '1';
    }
    sanitizePayload(payload) {
        if (!payload)
            return '';
        if (payload.length <= 4000)
            return payload;
        return payload.substring(0, 4000);
    }
    textOrNull(value) {
        if (!value || value.trim().length === 0)
            return null;
        return value.trim();
    }
    parsePositiveLong(value) {
        const text = this.textOrNull(value !== null && value !== undefined ? String(value) : null);
        if (!text)
            return null;
        try {
            const parsed = parseInt(text, 10);
            return parsed > 0 ? parsed : null;
        }
        catch {
            return null;
        }
    }
}
//# sourceMappingURL=fiat-payment-callback.service.js.map