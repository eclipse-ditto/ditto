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

import { getConfig } from "./common";

/**
 * Format string with placeholders like "Hello {0}, you are {1} years old"
 * @param str - Template string with {0}, {1}, etc placeholders
 * @param args - Values to replace placeholders
 * @returns Formatted string
 */
export function formatString(str: string, ...args: any[]): string {
    return str.replace(/{([0-9]+)}/g, function (match, index) {
        return typeof args[index] == 'undefined' ? match : args[index];
    });
}

/**
 * Parse duration string like "30s", "5m", "1h" to milliseconds
 * @param durationStr - Duration string (e.g., "30s", "5m", "1h", or raw milliseconds)
 * @returns Duration in milliseconds
 */
export function parseDuration(durationStr: string): number {
    const match = durationStr.match(/^(\d+)([smh])$/);
    if (!match) {
        return parseInt(durationStr); // Assume milliseconds if no unit
    }

    const value = parseInt(match[1]);
    const unit = match[2];

    switch (unit) {
        case 's': return value * 1000;
        case 'm': return value * 60 * 1000;
        case 'h': return value * 60 * 60 * 1000;
        default: return value;
    }
}

/**
 * Log only when DEBUG_LOGGING is enabled
 */
export const debugLog = (...args: any[]) => {
    if (process.env.DEBUG_LOGGING !== undefined) console.debug(...args);
};
