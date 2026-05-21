import { decryptAES } from '../crypto/ecpay-aes.js';
import { buildCheckMacValue } from '../crypto/ecpay-checkmac.js';
import type { EnvironmentConfig } from '@ltdjms/shared';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { FiatOrder } from '../domain/fiat-order.js';
import type { EcpayCallbackPayload } from '../domain/ecpay-callback-payload.js';
import pino from 'pino';

export interface CallbackResult {
  httpStatus: number;
  responseBody: string;
}

export const CallbackResult = {
  ok(): CallbackResult {
    return { httpStatus: 200, responseBody: '1|OK' };
  },
  fail(status: number): CallbackResult {
    return { httpStatus: status, responseBody: '0|FAIL' };
  },
};

class InvalidCallbackPayloadException extends Error {
  constructor(message: string, cause?: Error) {
    super(message);
    this.name = 'InvalidCallbackPayloadException';
    if (cause) this.cause = cause;
  }
}

export class FiatPaymentCallbackService {
  private readonly log: pino.Logger;

  constructor(
    private readonly config: EnvironmentConfig,
    private readonly fiatOrderRepository: FiatOrderRepository,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async handleCallback(
    requestBody: unknown,
    contentType: string | null,
  ): Promise<CallbackResult> {
    if (!requestBody) {
      return CallbackResult.fail(400);
    }

    try {
      // Normalize to a parsed object: if requestBody is already an object
      // (parsed by express body parser), use it directly to avoid the unnecessary
      // JSON.stringify → JSON.parse round-trip (P1-16).
      const bodyObj: Record<string, unknown> = typeof requestBody === 'object' && requestBody !== null
        ? (requestBody as Record<string, unknown>)
        : this.parseBodyString(String(requestBody), contentType);

      // Validate CheckMacValue before processing — prevents payload tampering
      if (!this.validateCheckMacValue(bodyObj)) {
        this.log.warn({}, 'ECPay callback rejected: CheckMacValue mismatch');
        return CallbackResult.fail(400);
      }

      const callbackPayload = this.truncateTo(JSON.stringify(bodyObj), 4000);

      const { node: callbackNode, rawDecrypted } = this.parseCallbackNode(bodyObj, contentType);
      const orderNumber = this.extractOrderNumber(callbackNode);
      if (!orderNumber || orderNumber.trim().length === 0) {
        this.log.warn({ payload: callbackPayload }, 'ECPay callback missing order number');
        return CallbackResult.fail(400);
      }

      const tradeStatus = this.extractTradeStatus(callbackNode);
      const paymentMessage = this.extractPaymentMessage(callbackNode);
      const truncatedPaymentMessage = paymentMessage !== null && paymentMessage.length > 512
        ? paymentMessage.substring(0, 512)
        : paymentMessage;
      const paid = this.isPaidStatus(tradeStatus);

      return await this.processWithOrderAsync(
        orderNumber,
        tradeStatus,
        truncatedPaymentMessage,
        paid,
        callbackPayload,
        callbackNode,
      );
    } catch (e) {
      if (e instanceof InvalidCallbackPayloadException) {
        this.log.warn({ reason: e.message }, 'Reject invalid ECPay callback payload');
        return CallbackResult.fail(400);
      }
      this.log.error({ error: e }, 'Failed to process ECPay callback payload');
      return CallbackResult.fail(500);
    }
  }

  private async processWithOrderAsync(
    orderNumber: string,
    tradeStatus: string | null,
    paymentMessage: string | null,
    paid: boolean,
    callbackPayload: string,
    callbackNode: EcpayCallbackPayload,
  ): Promise<CallbackResult> {
    const order = await this.fiatOrderRepository.findByOrderNumber(orderNumber);
    if (!order) {
      this.log.warn({ orderNumber }, 'ECPay callback order not found');
      return CallbackResult.ok();
    }

    if (!paid) {
      await this.fiatOrderRepository.updateCallbackStatus(
        orderNumber,
        tradeStatus,
        paymentMessage,
        callbackPayload,
      );
      this.log.info(
        { orderNumber, tradeStatus, rtnCode: callbackNode.RtnCode ?? -1 },
        'ECPay callback recorded unpaid status',
      );
      return CallbackResult.ok();
    }

    if (paid && !this.isValidPaidCallback(callbackNode, order, orderNumber)) {
      await this.fiatOrderRepository.updateCallbackStatus(
        orderNumber,
        tradeStatus,
        paymentMessage,
        callbackPayload,
      );
      this.log.warn({ orderNumber }, 'ECPay callback rejected paid transition due to validation failure');
      return CallbackResult.ok();
    }

    const paidOrder = await this.fiatOrderRepository.markPaidIfPending(
      orderNumber,
      tradeStatus ?? '',
      paymentMessage,
      callbackPayload,
      new Date(),
    );

    if (!paidOrder) {
      await this.fiatOrderRepository.updateCallbackStatus(
        orderNumber,
        tradeStatus,
        paymentMessage,
        callbackPayload,
      );
      // Re-query the order to get the authoritative current status,
      // because the original `order` variable may be stale due to race conditions
      // between the initial load and the markPaidIfPending UPDATE.
      const refreshedOrder = await this.fiatOrderRepository.findByOrderNumber(orderNumber);
      if (!refreshedOrder || refreshedOrder.status === 'EXPIRED') {
        this.log.info({ orderNumber }, 'ECPay callback arrived after fiat order expiry');
      } else if (refreshedOrder.status === 'PENDING_PAYMENT') {
        this.log.warn(
          { orderNumber },
          'markPaidIfPending returned null despite PENDING_PAYMENT status (concurrent update conflict or race condition)',
        );
      } else {
        this.log.info({ orderNumber }, 'ECPay callback duplicated paid notification');
      }
      return CallbackResult.ok();
    }

    this.log.info({ orderNumber: paidOrder.orderNumber }, 'ECPay callback marked fiat order paid');
    return CallbackResult.ok();
  }

  /**
   * Parse a raw string body (JSON or form-encoded) into a parsed object.
   * This is used when the body arrives as a string rather than pre-parsed by express.
   */
  private parseBodyString(body: string, contentType: string | null): Record<string, unknown> {
    if (this.isJson(contentType, body)) {
      return JSON.parse(body);
    }
    const formData = this.parseFormBody(body);
    if (formData && formData.size > 0) {
      const obj: Record<string, string> = {};
      for (const [key, value] of formData) {
        obj[key] = value;
      }
      return obj;
    }
    return JSON.parse(body);
  }

  private parseCallbackNode(bodyObj: Record<string, unknown>, contentType: string | null): { node: EcpayCallbackPayload; rawDecrypted: string } {
    let encryptedData: string | null = null;
    if (bodyObj && bodyObj.Data !== null && bodyObj.Data !== undefined) {
      encryptedData = String(bodyObj.Data);
    }
    if (!encryptedData || encryptedData.trim().length === 0) {
      throw new InvalidCallbackPayloadException('callback payload missing encrypted Data');
    }

    return this.parseDecryptedData(encryptedData);
  }

  private parseDecryptedData(encryptedData: string): { node: EcpayCallbackPayload; rawDecrypted: string } {
    const hashKey = this.config.getEcpayHashKey();
    const hashIv = this.config.getEcpayHashIv();
    if (!hashKey || hashKey.trim().length === 0 || !hashIv || hashIv.trim().length === 0) {
      throw new InvalidCallbackPayloadException('ECPAY_HASH_KEY / ECPAY_HASH_IV are required for callback');
    }
    try {
      const decryptedJson = decryptAES(encryptedData, hashKey, hashIv);
      return { node: JSON.parse(decryptedJson), rawDecrypted: decryptedJson };
    } catch (e) {
      throw new InvalidCallbackPayloadException('callback payload decryption failed', e as Error);
    }
  }

  private parseFormBody(body: string): Map<string, string> {
    const data = new Map<string, string>();
    const parts = body.split('&');
    for (const part of parts) {
      if (!part || part.trim().length === 0) continue;
      const eqIndex = part.indexOf('=');
      if (eqIndex <= 0) continue;
      let key: string;
      let value: string;
      try {
        key = decodeURIComponent(part.substring(0, eqIndex));
        value = decodeURIComponent(part.substring(eqIndex + 1));
      } catch {
        // Skip malformed URI-encoded segments in the form body
        continue;
      }
      data.set(key, value);
    }
    return data;
  }

  private isJson(contentType: string | null, body: string): boolean {
    if (contentType && contentType.toLowerCase().includes('application/json')) {
      return true;
    }
    const trimmed = body.trim();
    return trimmed.startsWith('{') && trimmed.endsWith('}');
  }

  private extractOrderNumber(callbackNode: EcpayCallbackPayload): string | null {
    const direct = this.textOrNull(callbackNode.MerchantTradeNo ?? null);
    if (direct) return direct;
    return this.textOrNull(callbackNode.OrderInfo?.MerchantTradeNo ?? null);
  }

  private extractTradeStatus(callbackNode: EcpayCallbackPayload): string | null {
    const direct = this.textOrNull(callbackNode.TradeStatus ?? null);
    if (direct) return direct;
    return this.textOrNull(callbackNode.OrderInfo?.TradeStatus ?? null);
  }

  private extractPaymentMessage(callbackNode: EcpayCallbackPayload): string | null {
    const rtnMsg = this.textOrNull(callbackNode.RtnMsg ?? null);
    if (rtnMsg) return rtnMsg;
    return this.textOrNull(callbackNode.TradeMsg ?? null);
  }

  private extractMerchantId(callbackNode: EcpayCallbackPayload): string | null {
    const direct = this.textOrNull(callbackNode.MerchantID ?? null);
    if (direct) return direct;
    return this.textOrNull(callbackNode.OrderInfo?.MerchantID ?? null);
  }

  private extractTradeAmount(callbackNode: EcpayCallbackPayload): number | null {
    const direct = this.parsePositiveLong(callbackNode.TradeAmt ?? null);
    if (direct !== null) return direct;
    const nestedTradeAmt = this.parsePositiveLong(callbackNode.OrderInfo?.TradeAmt ?? null);
    if (nestedTradeAmt !== null) return nestedTradeAmt;
    return this.parsePositiveLong(callbackNode.OrderInfo?.TotalAmount ?? null);
  }

  /**
   * Validates the CheckMacValue of the incoming ECPay callback payload.
   * Rebuilds the hash from all outer parameters (excluding CheckMacValue itself)
   * and compares against the provided CheckMacValue.
   * Prevents payload tampering per ECPay security specification.
   */
  private validateCheckMacValue(bodyObj: Record<string, unknown>): boolean {
    const hashKey = this.config.getEcpayHashKey();
    const hashIv = this.config.getEcpayHashIv();
    if (!hashKey || !hashIv) {
      this.log.warn('ECPAY_HASH_KEY / ECPAY_HASH_IV not configured — skipping CheckMacValue validation');
      return true;
    }

    const providedCheckMacValue = bodyObj['CheckMacValue'];
    if (!providedCheckMacValue || String(providedCheckMacValue).trim().length === 0) {
      this.log.warn('ECPay callback missing CheckMacValue');
      return false;
    }

    // Build params record from bodyObj excluding CheckMacValue
    const params: Record<string, string> = {};
    for (const [key, value] of Object.entries(bodyObj)) {
      if (key === 'CheckMacValue') continue;
      if (value !== null && value !== undefined) {
        params[key] = String(value);
      }
    }

    const expectedHash = buildCheckMacValue(params, hashKey, hashIv);
    const actualHash = String(providedCheckMacValue).trim();

    if (expectedHash !== actualHash) {
      this.log.warn(
        { expectedHash, actualHash },
        'ECPay callback CheckMacValue mismatch',
      );
      return false;
    }

    return true;
  }

  private isValidPaidCallback(callbackNode: EcpayCallbackPayload, order: FiatOrder, orderNumber: string): boolean {
    const expectedMerchantId = this.textOrNull(this.config.getEcpayMerchantId());
    if (expectedMerchantId) {
      const callbackMerchantId = this.extractMerchantId(callbackNode);
      if (!callbackMerchantId || expectedMerchantId !== callbackMerchantId) {
        this.log.warn(
          { orderNumber, expectedMerchantId, callbackMerchantId },
          'ECPay callback merchant mismatch',
        );
        return false;
      }
    }

    const callbackAmount = this.extractTradeAmount(callbackNode);
    if (callbackAmount === null) {
      this.log.warn({ orderNumber }, 'ECPay callback missing valid TradeAmt for paid status');
      return false;
    }
    if (callbackAmount !== order.amountTwd) {
      this.log.warn(
        { orderNumber, expectedAmount: order.amountTwd, callbackAmount },
        'ECPay callback amount mismatch',
      );
      return false;
    }

    return true;
  }

  private isPaidStatus(tradeStatus: string | null): boolean {
    return tradeStatus === '1';
  }

  private truncateTo(value: string | null, maxLength: number): string {
    if (!value) return '';
    if (value.length <= maxLength) return value;
    return value.substring(0, maxLength);
  }

  private textOrNull(value: string | null): string | null {
    if (!value || value.trim().length === 0) return null;
    return value.trim();
  }

  private parsePositiveLong(value: unknown): number | null {
    const text = this.textOrNull(value !== null && value !== undefined ? String(value) : null);
    if (!text) return null;
    try {
      const parsed = Number.parseInt(text, 10);
      return parsed > 0 ? parsed : null;
    } catch {
      return null;
    }
  }
}
