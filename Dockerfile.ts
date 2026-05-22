FROM node:22-alpine
WORKDIR /app

RUN corepack enable

# Copy source
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.json ./
COPY apps/ ./apps/
COPY packages/ ./packages/

# Install + build in one stage
RUN pnpm install --frozen-lockfile --prefer-offline && \
    pnpm build

# Prompts
COPY prompts ./prompts

# Create symlink for migration path (main.ts looks for ./db/migrations)
RUN ln -s /app/packages/shared/db/migrations /app/db/migrations || \
    mkdir -p /app/db && cp -r /app/packages/shared/db/migrations /app/db/

RUN addgroup -S botuser && adduser -S botuser -G botuser && \
    chown -R botuser:botuser /app
USER botuser

CMD ["node", "apps/bot/dist/main.js"]
