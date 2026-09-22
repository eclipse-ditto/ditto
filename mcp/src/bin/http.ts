#!/usr/bin/env node
/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { loadConfig } from "../config/load.js";
import { createHttpApp } from "../server/http-app.js";
import { buildKnowledgeService } from "../knowledge/build.js";

const config = loadConfig(process.env.DITTO_MCP_CONFIG);
const knowledge = await buildKnowledgeService(config);
const app = createHttpApp(config, knowledge);
app.listen(config.server.http.port, config.server.http.host, () => {
  process.stderr.write(
    `[ditto-mcp] http server listening on ${config.server.http.host}:${config.server.http.port}/mcp\n`,
  );
});
