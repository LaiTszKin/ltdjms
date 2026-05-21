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
  /** Track active connections for graceful shutdown. */
  private connections = new Set<any>();

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
    this.app.use(express.json({ limit: '64kb' }));
    this.app.use(express.urlencoded({ extended: true, limit: '64kb' }));

    // Callback route - POST only
    this.app.post(callbackPath, async (req, res) => {
      try {
        const result = await this.callbackService.handleCallback(
          req.body,
          req.headers['content-type'] ?? null,
        );
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

    // Enforce 30s request timeout to prevent slow client attacks
    this.server.setTimeout(30_000);

    // Track active connections for graceful shutdown (P3-8)
    this.server.on('connection', (socket) => {
      this.connections.add(socket);
      socket.on('close', () => this.connections.delete(socket));
      socket.on('error', () => {
        this.connections.delete(socket);
        // P3-6: Defensive delayed cleanup in case close never fires
        setTimeout(() => this.connections.delete(socket), 30000).unref();
      });
    });

    // Handle server-level errors (P1-8)
    this.server.on('error', (err: NodeJS.ErrnoException) => {
      if (err.code === 'EADDRINUSE') {
        this.log.error(
          { port: bindPort },
          `Port ${bindPort} is already in use. Please free the port or configure a different port via ECPAY_CALLBACK_BIND_PORT.`,
        );
      } else {
        this.log.error({ error: err }, 'ECPay callback server error');
      }
    });

    this.started = true;
  }

  async stop(): Promise<void> {
    if (this.server) {
      return new Promise<void>((resolve) => {
        this.server!.close(() => {
          // Destroy any remaining idle connections (P3-8)
          for (const socket of this.connections) {
            socket.destroy();
          }
          this.connections.clear();
          this.server = null;
          this.app = null;
          this.started = false;
          this.log.info('ECPay callback server stopped');
          resolve();
        });
      });
    }
    this.app = null;
    this.started = false;
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
    return '<html><body><h1>ECPay Callback Server is running</h1></body></html>';
  }
}
