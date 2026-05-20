import type { EnvironmentConfig } from '@ltdjms/shared';
import { Result, DomainError } from '@ltdjms/shared';
import pino from 'pino';
export interface CvsPaymentCode {
    orderNumber: string;
    paymentNo: string;
    expireDate: string | null;
    expireAt: Date;
    paymentUrl: string | null;
}
export declare class EcpayCvsPaymentService {
    private readonly config;
    private readonly log;
    private lastTradeNoMillis;
    private tradeNoSequence;
    constructor(config: EnvironmentConfig, logger?: pino.Logger);
    generateCvsPaymentCode(totalAmountTwd: number, itemName: string, tradeDesc: string): Promise<Result<CvsPaymentCode, DomainError>>;
    private buildRequestDataPayload;
    private generateMerchantTradeNo;
    private clampCvsExpireMinutes;
    private resolveExpireAt;
    private parseExpireAt;
    private buildTransCodeFailureMessage;
}
