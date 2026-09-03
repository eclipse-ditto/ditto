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

import { PostgreSqlContainer } from "@testcontainers/postgresql";

export async function startPgVector(): Promise<{ connectionString: string; stop: () => Promise<void> }> {
  const container = await new PostgreSqlContainer("pgvector/pgvector:pg16").start();
  return {
    connectionString: container.getConnectionUri(),
    stop: () => container.stop().then(() => undefined),
  };
}
