import type { EnvironmentConfig } from '@ltdjms/shared';
import { Result, DomainError } from '@ltdjms/shared';
import pino from 'pino';
export interface QueryTradeResult {
    orderNumber: string;
    paid: boolean;
    tradeStatus: string | null;
    tradeNo: string | null;
    tradeAmount: number;
    message: string | null;
}
export declare class EcpayTradeQueryService {
    private readonly config;
    private readonly log;
    constructor(config: EnvironmentConfig, logger?: pino.Logger);
    queryTrade(orderNumber: string): Promise<Result<QueryTradeResult, DomainError>>;
    private buildFormBody;
    private parseFormBody;
    private parseLongOrDefault;
    private textOrNull;
    private firstNonBlank;
}
