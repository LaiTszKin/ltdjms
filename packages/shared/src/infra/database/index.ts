export { createDatabasePool, type DatabaseConfig } from './connection.js';
export { runMigrations } from './migration-runner.js';
export { SchemaMigrationException } from './schema-migration-exception.js';
// NOTE: Drizzle schema definitions live in each package (economy, shop, dispatch, ai).
// The shared package only provides the connection pool and migration runner infrastructure.
// Domain-specific schemas in ./schema/ have been removed to eliminate duplication drift.
// See: packages/{economy,shop,dispatch,ai}/src/{domain,schema,persistence}/schema.ts
