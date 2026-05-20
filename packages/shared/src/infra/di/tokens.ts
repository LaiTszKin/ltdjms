import { type EnvironmentConfig } from '../config/environment-config.js';
import { type CacheService } from '../cache/cache-service.js';
import { type CacheKeyGenerator } from '../cache/cache-key-generator.js';
import { type DomainEventPublisher } from '../events/domain-event-publisher.js';
import { type DomainEvent } from '../../types/events/domain-event.js';
import { type DiscordRuntimeGateway } from '../../discord/domain/discord-runtime-gateway.js';
import { type DiscordEmbedBuilder } from '../../discord/domain/discord-embed-builder.js';
import type pino from 'pino';
import { type Pool } from 'pg';

/** Injection tokens for all shared services. */
export const TOKENS = {
  EnvironmentConfig: Symbol('EnvironmentConfig'),
  CacheService: Symbol('CacheService'),
  CacheKeyGenerator: Symbol('CacheKeyGenerator'),
  DomainEventPublisher: Symbol('DomainEventPublisher'),
  DiscordRuntimeGateway: Symbol('DiscordRuntimeGateway'),
  DiscordEmbedBuilder: Symbol('DiscordEmbedBuilder'),
  Logger: Symbol('Logger'),
  DatabasePool: Symbol('DatabasePool'),
  /** Token for registering multiple domain event listeners. */
  EventListeners: Symbol('EventListeners'),
} as const;

export type TokenMap = {
  EnvironmentConfig: EnvironmentConfig;
  CacheService: CacheService;
  CacheKeyGenerator: CacheKeyGenerator;
  DomainEventPublisher: DomainEventPublisher;
  DiscordRuntimeGateway: DiscordRuntimeGateway;
  DiscordEmbedBuilder: DiscordEmbedBuilder;
  Logger: pino.Logger;
  DatabasePool: Pool;
  EventListeners: Array<(event: DomainEvent) => void>;
};
