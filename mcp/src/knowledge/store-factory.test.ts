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

import { describe, it, expect, afterEach } from "vitest";
import { AppConfigSchema } from "../config/schema.js";
import { openStore } from "./store-factory.js";
import type { KnowledgeStore } from "./knowledge-store.js";

let store: KnowledgeStore;
afterEach(async () => { await store?.close(); });

describe("openStore", () => {
  it("opens an in-memory sqlite store by default", async () => {
    store = await openStore(AppConfigSchema.parse({}), { path: ":memory:" });
    expect(await store.isPopulated()).toBe(false);
  });
});
