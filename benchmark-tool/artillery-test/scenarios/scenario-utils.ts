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

import { debugLog } from '../utils';

// Re-export parseDuration from utils.ts for convenience
export { parseDuration } from '../utils';

/**
 * Execute an async function and emit metrics to Artillery
 * @param fn - Async function to execute
 * @param operation - Operation name (e.g., 'getThing', 'modifyThing')
 * @param events - Artillery events object for metric emission
 * @param context - Optional Artillery context with scenario/channel info
 */
export async function executeWithMetrics<T>(
    fn: () => Promise<T>,
    operation: string,
    events: any,
    context?: any
): Promise<void> {
    const start = Date.now();
    const scenarioName = context?.vars?.scenarioName;

    // Build metric prefixes: operation-level and scenario-level (scenarioName already includes channel, e.g., readThings_http)
    const prefixes = [operation];
    if (scenarioName) {
        prefixes.push(scenarioName);
    }

    try {
        await fn();
        const responseTime = Date.now() - start;

        // Emit success metrics (only on success)
        if (events && typeof events.emit === 'function') {
            for (const prefix of prefixes) {
                events.emit('counter', `${prefix}.requests`, 1);
                events.emit('counter', `${prefix}.success`, 1);
                events.emit('histogram', `${prefix}.response_time`, responseTime);
            }
        } else {
            console.warn(`[executeWithMetrics] WARNING: events invalid for ${operation}. Type: ${typeof events}, hasEmit: ${events ? typeof events.emit : 'N/A'}`);
        }
    } catch (error: any) {
        const responseTime = Date.now() - start;

        // Emit error metrics (count request but mark as error)
        if (events && typeof events.emit === 'function') {
            for (const prefix of prefixes) {
                events.emit('counter', `${prefix}.requests`, 1);
                events.emit('counter', `${prefix}.errors`, 1);
                events.emit('histogram', `${prefix}.response_time`, responseTime);
            }
        }

        // Each scenario handles this error to log specific data
        throw error;
    }
}
