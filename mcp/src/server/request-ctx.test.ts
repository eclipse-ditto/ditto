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

import { describe, it, expect } from "vitest";
import { AppConfigSchema } from "../config/schema.js";
import { buildCtx } from "./request-ctx.js";

describe("buildCtx", () => {
  const config = AppConfigSchema.parse({});

  it("maps sessionId, headers, and signal from extra", () => {
    const controller = new AbortController();
    const ctx = buildCtx(config, {
      sessionId: "sess-1",
      requestInfo: { headers: { "x-test": "yes" } },
      signal: controller.signal,
    });
    expect(ctx.config).toBe(config);
    expect(ctx.sessionId).toBe("sess-1");
    expect(ctx.headers).toEqual({ "x-test": "yes" });
    expect(ctx.signal).toBe(controller.signal);
  });

  it("tolerates a minimal extra (stdio has no headers/session)", () => {
    const ctx = buildCtx(config, {});
    expect(ctx.config).toBe(config);
    expect(ctx.sessionId).toBeUndefined();
    expect(ctx.headers).toBeUndefined();
    expect(ctx.signal).toBeUndefined();
  });
});
