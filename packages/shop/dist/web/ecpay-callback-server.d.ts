import type { EnvironmentConfig } from '@ltdjms/shared';
import type { FiatPaymentCallbackService } from '../services/fiat-payment-callback.service.js';
import pino from 'pino';
export declare class EcpayCallbackHttpServer {
    private readonly config;
    private readonly callbackService;
    private readonly log;
    private app;
    private server;
    private started;
    constructor(config: EnvironmentConfig, callbackService: FiatPaymentCallbackService, logger?: pino.Logger);
    start(): void;
    stop(): void;
    private sanitizeBindHost;
    private normalizeBindPort;
    private normalizePath;
    private isPubliclyExposedBindHost;
    private getLandingPageHtml;
}
