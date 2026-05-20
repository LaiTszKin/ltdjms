import { decryptAES } from '../crypto/ecpay-aes.js';
import type { EnvironmentConfig } from '@ltdjms/shared';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import type { FiatOrder } from '../domain/fiat-order.js';
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
    requestBody: string | null,
    contentType: string | null,
  ): Promise<CallbackResult> {
    if (!requestBody || requestBody.trim().length === 0) {
      return CallbackResult.fail(400);
    }

    try {
      const { node: callbackNode, rawDecrypted } = this.parseCallbackNode(requestBody, contentType);
      const callbackPayload = this.truncateTo(rawDecrypted, 4000);
      const orderNumber = this.extractOrderNumber(callbackNode);
      if (!orderNumber || orderNumber.trim().length === 0) {
        this.log.warn({ payload: callbackPayload }, 'ECPay callback missing order number');
        return CallbackResult.fail(400);
      }

      const tradeStatus = this.extractTradeStatus(callbackNode);
      const paymentMessage = this.extractPaymentMessage(callbackNode);
      const paid = this.isPaidStatus(tradeStatus);

      return await this.processWithOrderAsync(
        orderNumber,
        tradeStatus,
        paymentMessage,
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
    callbackNode: any,
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
      if (this.isExpiredStatus(order)) {
        this.log.info({ orderNumber }, 'ECPay callback arrived after fiat order expiry');
      } else {
        this.log.info({ orderNumber }, 'ECPay callback duplicated paid notification');
      }
      return CallbackResult.ok();
    }

    this.log.info({ orderNumber: paidOrder.orderNumber }, 'ECPay callback marked fiat order paid');
    return CallbackResult.ok();
  }

  private isExpiredStatus(order: FiatOrder): boolean {
    return order.status === 'EXPIRED';
  }

  private parseCallbackNode(requestBody: string, contentType: string | null): { node: any; rawDecrypted: string } {
    let parsedJson: any = null;
    let formData: Map<string, string> | null = null;

    try {
      if (this.isJson(contentType, requestBody)) {
        parsedJson = JSON.parse(requestBody);
      } else {
        formData = this.parseFormBody(requestBody);
        if (!formData || formData.size === 0) {
          parsedJson = JSON.parse(requestBody);
        }
      }
    } catch (e) {
      throw new InvalidCallbackPayloadException('callback payload parsing failed', e as Error);
    }

    let encryptedData: string | null = null;
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

  private parseDecryptedData(encryptedData: string): { node: any; rawDecrypted: string } {
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

  private extractOrderNumber(callbackNode: any): string | null {
    const direct = this.textOrNull(callbackNode.MerchantTradeNo ?? null);
    if (direct) return direct;
    return this.textOrNull(callbackNode.OrderInfo?.MerchantTradeNo ?? null);
  }

  private extractTradeStatus(callbackNode: any): string | null {
    const direct = this.textOrNull(callbackNode.TradeStatus ?? null);
    if (direct) return direct;
    return this.textOrNull(callbackNode.OrderInfo?.TradeStatus ?? null);
  }

  private extractPaymentMessage(callbackNode: any): string | null {
    const rtnMsg = this.textOrNull(callbackNode.RtnMsg ?? null);
    if (rtnMsg) return rtnMsg;
    return this.textOrNull(callbackNode.TradeMsg ?? null);
  }

  private extractMerchantId(callbackNode: any): string | null {
    const direct = this.textOrNull(callbackNode.MerchantID ?? null);
    if (direct) return direct;
    return this.textOrNull(callbackNode.OrderInfo?.MerchantID ?? null);
  }

  private extractTradeAmount(callbackNode: any): number | null {
    const direct = this.parsePositiveLong(callbackNode.TradeAmt ?? null);
    if (direct !== null) return direct;
    const nestedTradeAmt = this.parsePositiveLong(callbackNode.OrderInfo?.TradeAmt ?? null);
    if (nestedTradeAmt !== null) return nestedTradeAmt;
    return this.parsePositiveLong(callbackNode.OrderInfo?.TotalAmount ?? null);
  }

  private isValidPaidCallback(callbackNode: any, order: FiatOrder, orderNumber: string): boolean {
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

  private parsePositiveLong(value: any): number | null {
    const text = this.textOrNull(value !== null && value !== undefined ? String(value) : null);
    if (!text) return null;
    try {
      const parsed = parseInt(text, 10);
      return parsed > 0 ? parsed : null;
    } catch {
      return null;
    }
  }
}
