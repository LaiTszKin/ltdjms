import { encryptAES, decryptAES } from '../crypto/ecpay-aes.js';
import { ok, err, DomainError } from '@ltdjms/shared';
import pino from 'pino';
const STAGE_ENDPOINT = 'https://ecpayment-stage.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode';
const PROD_ENDPOINT = 'https://ecpayment.ecpay.com.tw/1.0.0/Cashier/GenPaymentCode';
const OFFICIAL_STAGE_MERCHANT_ID = '3002607';
const OFFICIAL_STAGE_HASH_KEY = 'pwFHCqoQZGmho4w6';
const OFFICIAL_STAGE_HASH_IV = 'EkRm7iFT261dpevs';
// Reserved for future validation of MerchantTradeNo time component format (P2-18)
// Format: YYMMDDHHmmSSsss (year, month, day, hour, minute, second, millisecond)
// const MERCHANT_TRADE_NO_TIME_FORMAT = /^(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(\d{3})$/;
function pad2(n) {
    return n.toString().padStart(2, '0');
}
function pad3(n) {
    return n.toString().padStart(3, '0');
}
export class EcpayCvsPaymentService {
    config;
    log;
    lastTradeNoMillis = -1;
    tradeNoSequence = 0;
    constructor(config, logger) {
        this.config = config;
        this.log = logger ?? pino({ level: 'warn' });
    }
    async generateCvsPaymentCode(totalAmountTwd, itemName, tradeDesc) {
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
            return err(DomainError.invalidInput('綠界金流尚未完成設定（MerchantID/HashKey/HashIV/ReturnURL）'));
        }
        if (merchantId === OFFICIAL_STAGE_MERCHANT_ID &&
            hashKey === OFFICIAL_STAGE_HASH_KEY &&
            hashIv === OFFICIAL_STAGE_HASH_IV &&
            !this.config.getEcpayStageMode()) {
            return err(DomainError.invalidInput('目前設定的是綠界官方測試 MerchantID/HashKey/HashIV，但 ECPAY_STAGE_MODE=false。請切回測試環境或改用正式環境金鑰。'));
        }
        const requestAt = new Date();
        const merchantTradeNo = this.generateMerchantTradeNo();
        try {
            const dataPayload = this.buildRequestDataPayload(merchantId, merchantTradeNo, totalAmountTwd, itemName.trim(), tradeDesc && tradeDesc.trim().length > 0 ? tradeDesc.trim() : 'Discord 商品下單', returnUrl, this.clampCvsExpireMinutes(this.config.getEcpayCvsExpireMinutes()));
            const encryptedData = encryptAES(dataPayload, hashKey, hashIv);
            const root = {
                MerchantID: merchantId,
                RqHeader: { Timestamp: Math.floor(Date.now() / 1000) },
                Data: encryptedData,
            };
            const endpoint = this.config.getEcpayStageMode() ? STAGE_ENDPOINT : PROD_ENDPOINT;
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(root),
                signal: AbortSignal.timeout(15000),
            });
            if (!response.ok) {
                const body = await response.text();
                this.log.warn({ status: response.status, body }, 'ECPay request failed');
                return err(DomainError.unexpectedFailure('綠界服務暫時不可用，請稍後再試'));
            }
            const responseJson = await response.json();
            const transCode = responseJson.TransCode ?? -1;
            if (transCode !== 1) {
                const transMsg = responseJson.TransMsg ?? '未知錯誤';
                this.log.warn({ transCode, transMsg, merchantId, stageMode: this.config.getEcpayStageMode() }, 'ECPay transCode failed');
                return err(DomainError.unexpectedFailure(this.buildTransCodeFailureMessage(transMsg)));
            }
            const encryptedResponseData = responseJson.Data ?? '';
            if (!encryptedResponseData) {
                this.log.warn({ body: responseJson }, 'ECPay response data is empty');
                return err(DomainError.unexpectedFailure('綠界回傳資料不完整'));
            }
            let decryptedJson;
            try {
                decryptedJson = decryptAES(encryptedResponseData, hashKey, hashIv);
            }
            catch (e) {
                this.log.warn({ error: e }, 'Failed to decrypt ECPay response');
                return err(DomainError.unexpectedFailure('綠界回傳資料解密失敗'));
            }
            const dataNode = JSON.parse(decryptedJson);
            const rtnCode = dataNode.RtnCode ?? -1;
            if (rtnCode !== 1) {
                const rtnMsg = dataNode.RtnMsg ?? '未知錯誤';
                this.log.warn({ rtnCode, rtnMsg }, 'ECPay business failed');
                return err(DomainError.unexpectedFailure(`綠界取號失敗：${rtnMsg}`));
            }
            const orderInfo = dataNode.OrderInfo ?? {};
            const orderNumber = orderInfo.MerchantTradeNo ?? '';
            const cvsInfo = dataNode.CVSInfo ?? {};
            const paymentNoVal = cvsInfo.PaymentNo ?? '';
            const expireDateStr = cvsInfo.ExpireDate ?? null;
            const expireAt = this.resolveExpireAt(expireDateStr, requestAt, this.clampCvsExpireMinutes(this.config.getEcpayCvsExpireMinutes()));
            const paymentUrl = cvsInfo.PaymentURL ?? null;
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
        }
        catch (e) {
            if (e.name === 'AbortError') {
                this.log.warn('ECPay request timeout');
                return err(DomainError.unexpectedFailure('綠界連線逾時，請稍後再試'));
            }
            this.log.error({ error: e }, 'Failed to generate ECPay CVS payment code');
            return err(DomainError.unexpectedFailure('建立法幣訂單失敗，請稍後再試'));
        }
    }
    buildRequestDataPayload(merchantId, merchantTradeNo, totalAmountTwd, itemName, tradeDesc, returnUrl, cvsExpireMinutes) {
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
    generateMerchantTradeNo() {
        const now = new Date();
        let currentMillis = now.getTime();
        if (currentMillis < this.lastTradeNoMillis) {
            currentMillis = this.lastTradeNoMillis;
        }
        if (currentMillis === this.lastTradeNoMillis) {
            this.tradeNoSequence++;
            if (this.tradeNoSequence > 999) {
                currentMillis = this.lastTradeNoMillis + 1;
                this.tradeNoSequence = 0;
            }
        }
        else {
            this.tradeNoSequence = 0;
        }
        this.lastTradeNoMillis = currentMillis;
        const d = new Date(currentMillis);
        const yy = d.getFullYear().toString().slice(-2);
        const MM = pad2(d.getMonth() + 1);
        const dd = pad2(d.getDate());
        const HH = pad2(d.getHours());
        const mm = pad2(d.getMinutes());
        const ss = pad2(d.getSeconds());
        const SSS = pad3(d.getMilliseconds());
        const timePart = `${yy}${MM}${dd}${HH}${mm}${ss}${SSS}`;
        const sequencePart = pad3(this.tradeNoSequence);
        return `FD${timePart}${sequencePart}`;
    }
    clampCvsExpireMinutes(input) {
        if (input < 1)
            return 1;
        return Math.min(input, 43200);
    }
    resolveExpireAt(expireDate, fallbackBase, expireMinutes) {
        const parsed = this.parseExpireAt(expireDate);
        if (parsed)
            return parsed;
        return new Date(fallbackBase.getTime() + expireMinutes * 60 * 1000);
    }
    parseExpireAt(expireDate) {
        if (!expireDate || expireDate.trim().length === 0)
            return null;
        try {
            // Format: yyyy/MM/dd HH:mm:ss (Asia/Taipei)
            const parts = expireDate.trim().match(/^(\d{4})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2}):(\d{2})$/);
            if (!parts)
                return null;
            const taipeiDate = new Date(`${parts[1]}-${parts[2]}-${parts[3]}T${parts[4]}:${parts[5]}:${parts[6]}+08:00`);
            return taipeiDate;
        }
        catch {
            return null;
        }
    }
    buildTransCodeFailureMessage(transMsg) {
        if (transMsg && transMsg.toLowerCase().includes('decrypt fail')) {
            return (`綠界取號失敗：${transMsg}。請確認 ECPAY_STAGE_MODE 是否和 MerchantID/HashKey/HashIV 對應同一環境，並檢查金鑰是否有貼錯或多餘空白。`);
        }
        return `綠界取號失敗：${transMsg}`;
    }
}
//# sourceMappingURL=ecpay-cvs-payment.service.js.map