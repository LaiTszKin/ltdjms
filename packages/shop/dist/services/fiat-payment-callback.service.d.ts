import type { EnvironmentConfig } from '@ltdjms/shared';
import type { FiatOrderRepository } from '../domain/fiat-order-repository.js';
import pino from 'pino';
export interface CallbackResult {
    httpStatus: number;
    responseBody: string;
}
export declare const CallbackResult: {
    ok(): CallbackResult;
    fail(status: number): CallbackResult;
};
export declare class FiatPaymentCallbackService {
    private readonly config;
    private readonly fiatOrderRepository;
    private readonly log;
    constructor(config: EnvironmentConfig, fiatOrderRepository: FiatOrderRepository, logger?: pino.Logger);
    handleCallback(requestBody: string | null, contentType: string | null): Promise<CallbackResult>;
    private processWithOrderAsync;
    private isExpiredStatus;
    private parseCallbackNode;
    private parseDecryptedData;
    private parseFormBody;
    private isJson;
    private extractOrderNumber;
    private extractTradeStatus;
    private extractPaymentMessage;
    private extractMerchantId;
    private extractTradeAmount;
    private isValidPaidCallback;
    private isPaidStatus;
    private truncateTo;
    private textOrNull;
    private parsePositiveLong;
}
