import { encryptAES, decryptAES } from '../crypto/ecpay-aes.js';
import type { EnvironmentConfig } from '@ltdjms/shared';
import { Result, ok, err, DomainError } from '@ltdjms/shared';
import { Agent } from 'undici';
import { fetchWithRetry } from './fetch-retry.js';
import crypto from 'node:crypto';
import pino from 'pino';

const STAGE_ENDPOINT = 'https://ecpayment-stage.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode';
const PROD_ENDPOINT = 'https://ecpayment.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode';
// ECPay official stage (test) credentials — these are PUBLIC test credentials.
// They are embedded here for stage-mode detection: the application compares
// the user's .env values against these constants and refuses to start in
// production mode (ECPAY_STAGE_MODE=false) if stage credentials are detected.
// DO NOT add production credentials here — they belong in .env only.
const OFFICIAL_STAGE_MERCHANT_ID = '3002607';
const OFFICIAL_STAGE_HASH_KEY = 'pwFHCqoQZGmho4w6';
const OFFICIAL_STAGE_HASH_IV = 'EkRm7iFT261dpevs';

const keepAliveDispatcher = new Agent({ keepAliveTimeout: 10 });

function pad2(n: number): string {
  return n.toString().padStart(2, '0');
}

function pad3(n: number): string {
  return n.toString().padStart(3, '0');
}

export interface CvsPaymentCode {
  orderNumber: string;
  paymentNo: string;
  expireDate: string | null;
  expireAt: Date;
  paymentUrl: string | null;
}

export class EcpayCvsPaymentService {
  private readonly log: pino.Logger;

  constructor(
    private readonly config: EnvironmentConfig,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  async generateCvsPaymentCode(
    totalAmountTwd: number,
    itemName: string,
    tradeDesc: string,
  ): Promise<Result<CvsPaymentCode, DomainError>> {
    if (totalAmountTwd <= 0) {
      return err(DomainError.invalidInput('法幣付款金額必須大於 0'));
    }
    if (!itemName || itemName.trim().length === 0) {
      return err(DomainError.invalidInput('商品名稱不能為空'));
    }

    const merchantId = this.config.getEcpayMerchantId().trim();
    const hashKey = this.config.getEcpayHashKey().trim();
    const hashIv = this.config.getEcpayHashIv().trim();
    const returnUrl = this.config.getEcpayReturnUrl().trim();

    if (!merchantId || !hashKey || !hashIv || !returnUrl) {
      return err(
        DomainError.invalidInput('綠界金流尚未完成設定（MerchantID/HashKey/HashIV/ReturnURL）'),
      );
    }

    if (
      merchantId === OFFICIAL_STAGE_MERCHANT_ID &&
      hashKey === OFFICIAL_STAGE_HASH_KEY &&
      hashIv === OFFICIAL_STAGE_HASH_IV &&
      !this.config.getEcpayStageMode()
    ) {
      return err(
        DomainError.invalidInput(
          '目前設定的是綠界官方測試 MerchantID/HashKey/HashIV，但 ECPAY_STAGE_MODE=false。請切回測試環境或改用正式環境金鑰。',
        ),
      );
    }

    const requestAt = new Date();
    const merchantTradeNo = this.generateMerchantTradeNo();

    try {
      const dataPayload = this.buildRequestDataPayload(
        merchantId,
        merchantTradeNo,
        totalAmountTwd,
        itemName.trim(),
        tradeDesc && tradeDesc.trim().length > 0 ? tradeDesc.trim() : 'Discord 商品下單',
        returnUrl,
        this.clampCvsExpireMinutes(this.config.getEcpayCvsExpireMinutes()),
      );

      const encryptedData = encryptAES(dataPayload, hashKey, hashIv);

      const root = {
        MerchantID: merchantId,
        RqHeader: { Timestamp: Math.floor(Date.now() / 1000) },
        Data: encryptedData,
      };

      const endpoint = this.config.getEcpayStageMode() ? STAGE_ENDPOINT : PROD_ENDPOINT;

      const response = await fetchWithRetry(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(root),
        signal: AbortSignal.timeout(15000),
        dispatcher: keepAliveDispatcher,
      });

      if (!response.ok) {
        const body = await response.text();
        this.log.warn({ status: response.status, body }, 'ECPay request failed');
        return err(DomainError.unexpectedFailure('綠界服務暫時不可用，請稍後再試'));
      }

      const responseJson: any = await response.json();
      const transCode = responseJson.TransCode ?? -1;
      if (transCode !== 1) {
        const transMsg = responseJson.TransMsg ?? '未知錯誤';
        this.log.warn(
          { transCode, transMsg, merchantId, stageMode: this.config.getEcpayStageMode() },
          'ECPay transCode failed',
        );
        return err(DomainError.unexpectedFailure(this.buildTransCodeFailureMessage(transMsg)));
      }

      const encryptedResponseData: string = responseJson.Data ?? '';
      if (!encryptedResponseData) {
        this.log.warn({ body: responseJson }, 'ECPay response data is empty');
        return err(DomainError.unexpectedFailure('綠界回傳資料不完整'));
      }

      let decryptedJson: string;
      try {
        decryptedJson = decryptAES(encryptedResponseData, hashKey, hashIv);
      } catch (e) {
        this.log.warn({ error: e }, 'Failed to decrypt ECPay response');
        return err(DomainError.unexpectedFailure('綠界回傳資料解密失敗'));
      }

      const dataNode: any = JSON.parse(decryptedJson);
      const rtnCode = dataNode.RtnCode ?? -1;
      if (rtnCode !== 1) {
        const rtnMsg = dataNode.RtnMsg ?? '未知錯誤';
        this.log.warn({ rtnCode, rtnMsg }, 'ECPay business failed');
        return err(DomainError.unexpectedFailure(`綠界取號失敗：${rtnMsg}`));
      }

      const orderInfo = dataNode.OrderInfo ?? {};
      const orderNumber: string = orderInfo.MerchantTradeNo ?? '';
      const cvsInfo = dataNode.CVSInfo ?? {};
      const paymentNoVal: string = cvsInfo.PaymentNo ?? '';
      const expireDateStr: string | null = cvsInfo.ExpireDate ?? null;
      const expireAt = this.resolveExpireAt(
        expireDateStr,
        requestAt,
        this.clampCvsExpireMinutes(this.config.getEcpayCvsExpireMinutes()),
      );
      const paymentUrl: string | null = cvsInfo.PaymentURL ?? null;

      if (!orderNumber || !paymentNoVal) {
        this.log.warn({ decryptedJson }, 'ECPay response missing orderNumber or paymentNo');
        return err(DomainError.unexpectedFailure('綠界回傳資料不完整'));
      }

      return ok({
        orderNumber,
        paymentNo: paymentNoVal,
        expireDate: expireDateStr,
        expireAt,
        paymentUrl,
      });
    } catch (e: any) {
      if (e.name === 'TimeoutError' || e.name === 'AbortError') {
        this.log.warn('ECPay request timeout');
        return err(DomainError.unexpectedFailure('綠界連線逾時，請稍後再試'));
      }
      this.log.error({ error: e }, 'Failed to generate ECPay CVS payment code');
      return err(DomainError.unexpectedFailure('建立法幣訂單失敗，請稍後再試'));
    }
  }

  private buildRequestDataPayload(
    merchantId: string,
    merchantTradeNo: string,
    totalAmountTwd: number,
    itemName: string,
    tradeDesc: string,
    returnUrl: string,
    cvsExpireMinutes: number,
  ): string {
    const now = new Date();
    const tradeDate = `${now.getFullYear()}/${pad2(now.getMonth() + 1)}/${pad2(now.getDate())} ${pad2(now.getHours())}:${pad2(now.getMinutes())}:${pad2(now.getSeconds())}`;

    const data = {
      MerchantID: merchantId,
      ChoosePayment: 'CVS',
      OrderInfo: {
        MerchantTradeDate: tradeDate,
        MerchantTradeNo: merchantTradeNo,
        TotalAmount: totalAmountTwd,
        ReturnURL: returnUrl,
        TradeDesc: tradeDesc,
        ItemName: itemName,
      },
      CVSInfo: {
        ExpireDate: cvsExpireMinutes,
        CVSCode: 'CVS',
      },
    };

    return JSON.stringify(data);
  }

  // Atomic counter for same-millisecond sequence numbers
  // NOTE: In-memory static counter is safe under current single-process deployment.
  // If the application needs to scale horizontally in the future, this must be
  // replaced with a Redis atomic INCR or equivalent distributed sequence generator
  // to guarantee uniqueness across processes.
  private static sequenceCounter = 0;
  private static lastTimestampMs = 0;

  /**
   * Generates a unique MerchantTradeNo in format `FD{yyMMddHHmmssSSS}{3-digit-seq}`.
   *
   * Thread-safety in Node.js: this method is fully synchronous (no await points),
   * so it runs atomically within a single event-loop tick. Two concurrent async
   * callers cannot interleave inside this method. Safe for single-process.
   *
   * Multi-process: static counters are process-local — if horizontal scaling is
   * needed, replace with Redis INCR or crypto.randomUUID()-based generation.
   */
  private generateMerchantTradeNo(): string {
    const now = new Date();
    const yy = pad2(now.getFullYear() % 100);
    const MM = pad2(now.getMonth() + 1);
    const dd = pad2(now.getDate());
    const HH = pad2(now.getHours());
    const mm = pad2(now.getMinutes());
    const ss = pad2(now.getSeconds());
    const ms = pad3(now.getMilliseconds());
    const ts = `${yy}${MM}${dd}${HH}${mm}${ss}${ms}`;

    // Synchronized sequence: reset on timestamp change, increment on same ms
    const timestampMs = now.getTime();
    if (timestampMs !== EcpayCvsPaymentService.lastTimestampMs) {
      EcpayCvsPaymentService.lastTimestampMs = timestampMs;
      EcpayCvsPaymentService.sequenceCounter = 0;
    }
    const seq = pad3(EcpayCvsPaymentService.sequenceCounter);
    EcpayCvsPaymentService.sequenceCounter++;

    return `FD${ts}${seq}`;
  }

  private clampCvsExpireMinutes(input: number): number {
    if (input < 1) return 1;
    return Math.min(input, 43200);
  }

  private resolveExpireAt(
    expireDate: string | null,
    fallbackBase: Date,
    expireMinutes: number,
  ): Date {
    const parsed = this.parseExpireAt(expireDate);
    if (parsed) return parsed;
    return new Date(fallbackBase.getTime() + expireMinutes * 60 * 1000);
  }

  private parseExpireAt(expireDate: string | null): Date | null {
    if (!expireDate || expireDate.trim().length === 0) return null;
    try {
      // Format: yyyy/MM/dd HH:mm:ss (Asia/Taipei)
      const parts = expireDate.trim().match(/^(\d{4})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2}):(\d{2})$/);
      if (!parts) return null;
      const taipeiDate = new Date(
        `${parts[1]}-${parts[2]}-${parts[3]}T${parts[4]}:${parts[5]}:${parts[6]}+08:00`,
      );
      return taipeiDate;
    } catch {
      return null;
    }
  }

  private buildTransCodeFailureMessage(transMsg: string): string {
    if (transMsg && transMsg.toLowerCase().includes('decrypt fail')) {
      return `綠界取號失敗：${transMsg}。請確認 ECPAY_STAGE_MODE 是否和 MerchantID/HashKey/HashIV 對應同一環境，並檢查金鑰是否有貼錯或多餘空白。`;
    }
    return `綠界取號失敗：${transMsg}`;
  }
}
