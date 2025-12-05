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

FROM node:24-alpine AS base

WORKDIR /app


FROM base AS build
# Copy package files
COPY package*.json ./

# Copy source files and tsconfig
COPY *.ts tsconfig.json ./
COPY config/ ./config/
COPY channels/ ./channels/
COPY scenarios/ ./scenarios/

# Install build dependencies
RUN npm ci --omit=dev && \
    npm cache verify

RUN npm run build


FROM node:24-alpine AS runtime

WORKDIR /app

ENV NODE_ENV=docker \
    CONFIG_PATH=/app/config-kubernetes.yml

COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package*.json ./

COPY --from=build /app/dist ./dist

# for test results json file
RUN mkdir -p /results

# Run artillery test
CMD ["npm", "run", "test:dist"]
