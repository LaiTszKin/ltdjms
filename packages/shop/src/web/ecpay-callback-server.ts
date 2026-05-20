import express from 'express';
import type { EnvironmentConfig } from '@ltdjms/shared';
import type { FiatPaymentCallbackService } from '../services/fiat-payment-callback.service.js';
import pino from 'pino';
import http from 'node:http';

export class EcpayCallbackHttpServer {
  private readonly log: pino.Logger;
  private app: express.Express | null = null;
  private server: http.Server | null = null;
  private started = false;

  constructor(
    private readonly config: EnvironmentConfig,
    private readonly callbackService: FiatPaymentCallbackService,
    logger?: pino.Logger,
  ) {
    this.log = logger ?? pino({ level: 'warn' });
  }

  start(): void {
    if (this.started) return;

    const returnUrl = this.config.getEcpayReturnUrl();
    if (!returnUrl || returnUrl.trim().length === 0) {
      this.log.info(
        'Skip starting ECPay callback server because no effective return URL is configured (set APP_PUBLIC_BASE_URL or ECPAY_RETURN_URL)',
      );
      return;
    }

    const bindHost = this.sanitizeBindHost(this.config.getEcpayCallbackBindHost());
    const bindPort = this.normalizeBindPort(this.config.getEcpayCallbackBindPort());
    const callbackPath = this.normalizePath(this.config.getEcpayCallbackPath());

    if (callbackPath === '/' || callbackPath === '/index.html') {
      throw new Error('ECPAY callback 路徑不可與首頁路徑衝突');
    }

    if (this.config.getEcpayStageMode() && this.isPubliclyExposedBindHost(bindHost)) {
      throw new Error(
        'ECPAY_STAGE_MODE=true 時，callback server 不可綁定公開位址。請改用 127.0.0.1 / localhost / ::1，或切換正式環境設定。',
      );
    }

    this.app = express();

    // Body parsing middleware (global) for JSON and form-encoded payloads.
    // The parsed body is serialized back to a string for the callback service,
    // which handles both JSON and form-encoded payloads from ECPay.
    this.app.use(express.json({ limit: '64kb' }));
    this.app.use(express.urlencoded({ extended: true, limit: '64kb' }));

    // Callback route - POST only
    this.app.post(callbackPath, async (req, res) => {
      try {
        const bodyStr = typeof req.body === 'string' ? req.body : JSON.stringify(req.body ?? '');
        const contentType = req.headers['content-type'] ?? null;
        const result = await this.callbackService.handleCallback(bodyStr, contentType);
        res.status(result.httpStatus).send(result.responseBody);
      } catch (e) {
        this.log.error({ error: e }, 'ECPay callback handler error');
        res.status(500).send('0|FAIL');
      }
    });

    // Non-POST to callback route -> 405
    this.app.all(callbackPath, (_req, res) => {
      res.status(405).send('Method Not Allowed');
    });

    // Landing page
    this.app.get('/', (_req, res) => {
      res.status(200).type('html').send(this.getLandingPageHtml());
    });
    this.app.get('/index.html', (_req, res) => {
      res.status(200).type('html').send(this.getLandingPageHtml());
    });
    this.app.head('/', (_req, res) => {
      res.status(200).end();
    });
    this.app.head('/index.html', (_req, res) => {
      res.status(200).end();
    });

    // Non-GET/HEAD to landing page -> 405
    this.app.all(['/', '/index.html'], (_req, res) => {
      if (_req.method !== 'GET' && _req.method !== 'HEAD') {
        res.status(405).send('Method Not Allowed');
      }
    });

    this.server = this.app.listen(bindPort, bindHost, () => {
      this.log.info(
        { host: bindHost, port: bindPort, callbackPath },
        'ECPay callback server started',
      );
    });

    this.started = true;
  }

  stop(): void {
    if (this.server) {
      this.server.close();
      this.server = null;
    }
    this.app = null;
    this.started = false;
    this.log.info('ECPay callback server stopped');
  }

  private sanitizeBindHost(bindHost: string): string {
    if (!bindHost || bindHost.trim().length === 0) return '127.0.0.1';
    return bindHost.trim();
  }

  private normalizeBindPort(port: number): number {
    if (port < 1 || port > 65535) return 8085;
    return port;
  }

  private normalizePath(path: string): string {
    if (!path || path.trim().length === 0) return '/ecpay/callback';
    const normalized = path.trim();
    return normalized.startsWith('/') ? normalized : '/' + normalized;
  }

  private isPubliclyExposedBindHost(bindHost: string): boolean {
    const normalized = bindHost.trim().toLowerCase();
    return !(
      normalized === '127.0.0.1' ||
      normalized === 'localhost' ||
      normalized === '::1' ||
      normalized === '[::1]'
    );
  }

  private getLandingPageHtml(): string {
    return `<!DOCTYPE html>
<html lang="zh-TW">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LTDJMS - ECPay Callback Server</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 640px; margin: 40px auto; padding: 0 20px; color: #333; }
    h1 { color: #5865F2; }
    .status { background: #f0f0f0; padding: 16px; border-radius: 8px; margin: 20px 0; }
    code { background: #e8e8e8; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }
  </style>
</head>
<body>
  <h1>LTDJMS ECPay Callback Server</h1>
  <div class="status">
    <p>This server is running and ready to receive ECPay payment callbacks.</p>
  </div>
  <p>Callback endpoint: <code>POST ${this.config.getEcpayCallbackPath()}</code></p>
  <p>Return URL configured: <code>${this.config.getEcpayReturnUrl() || '(not set)'}</code></p>
</body>
</html>`;
  }
}
