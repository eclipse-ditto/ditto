# Copyright (c) 2026 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0

# ---- builder: compile native deps + tsc, then drop devDeps ----
# node:22-slim = Debian (glibc). Do NOT use alpine: onnxruntime-node
# (pulled by @huggingface/transformers) ships no musl prebuild.
FROM node:22-slim AS builder
WORKDIR /app

# Toolchain so better-sqlite3 can build from source if no prebuilt
# binary matches this platform/ABI. Builder is throwaway — not shipped.
RUN apt-get update && apt-get install -y --no-install-recommends \
      python3 make g++ ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install with full lockfile first (better layer caching).
COPY package.json package-lock.json ./
RUN npm ci

# Build TypeScript -> dist/
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Strip devDeps but keep the compiled native .node binaries.
RUN npm prune --omit=dev

# ---- runtime: slim image, prod deps + dist only, non-root ----
FROM node:22-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production

# Same base/arch as builder -> copying node_modules keeps native bindings valid.
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/package.json ./package.json

# Baked default config (http, 0.0.0.0). Override by bind-mounting a file
# and pointing DITTO_MCP_CONFIG at it.
COPY docker/config.docker.json ./config.docker.json
ENV DITTO_MCP_CONFIG=/app/config.docker.json

# Writable dir for the sqlite knowledge index (see config.docker.json).
# Mount a volume here to persist across restarts.
RUN mkdir -p /app/data

# Run unprivileged.
RUN useradd --system --uid 10001 --home-dir /app ditto \
    && chown -R ditto:ditto /app
USER ditto
VOLUME ["/app/data"]

EXPOSE 3000
CMD ["node", "dist/bin/http.js"]
