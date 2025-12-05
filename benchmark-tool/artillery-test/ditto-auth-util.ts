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

import { AuthProvider, DittoURL } from "@eclipse-ditto/ditto-javascript-client-node";
import { getConfig } from './common';
import { DittoAuthConfig } from './config/ditto-config';

const preAuthHeader = 'x-ditto-pre-authenticated';

/**
 * Creates auth provider for Ditto client using pre-authenticated header
 */
export function getPreAuthHeaderDittoAuthProvider(preAuthValue: string): AuthProvider {
    return {
        authenticateWithHeaders: (headers: Map<string, string>): Map<string, string> => {
            const headersMap = new Map(headers);
            headersMap.set(preAuthHeader, preAuthValue);
            return headersMap;
        },

        authenticateWithUrl: _authenticateWithUrl
    };
}

/**
 * Creates auth provider for Ditto client using basic authentication
 */
export function getBasicAuthDittoAuthProvider(user: string, password: string): AuthProvider {
    return {
        authenticateWithHeaders: (headers: Map<string, string>): Map<string, string> => {
            const headersMap = new Map(headers);
            headersMap.set('Authorization', `Basic ${btoa(`${user}:${password}`)}`);
            return headersMap;
        },

        authenticateWithUrl: _authenticateWithUrl
    };
}


function _authenticateWithUrl(originalUrl: DittoURL): DittoURL {
    return originalUrl;
}

/**
 * Checks if basic auth is configured (user and password are non-empty)
 */
export function isBasicAuthConfigured(auth?: DittoAuthConfig): boolean {
    const authConfig = auth || getConfig().ditto.auth;
    return !!(authConfig.user && authConfig.password);
}

/**
 * Returns the appropriate auth provider based on configuration.
 * Uses basic auth if user/password are configured, otherwise falls back to pre-authenticated header.
 */
export function getAuthProvider(auth?: DittoAuthConfig): AuthProvider {
    const authConfig = auth || getConfig().ditto.auth;
    if (isBasicAuthConfigured(authConfig)) {
        return getBasicAuthDittoAuthProvider(authConfig.user, authConfig.password);
    }
    return getPreAuthHeaderDittoAuthProvider(authConfig.preAuthenticatedHeaderValue);
}

/**
 * Returns auth headers as a Headers object based on configuration.
 * Uses basic auth if user/password are configured, otherwise falls back to pre-authenticated header.
 */
export function getAuthHeaders(auth?: DittoAuthConfig): Headers {
    const authConfig = auth || getConfig().ditto.auth;
    const headers = new Headers();
    if (isBasicAuthConfigured(authConfig)) {
        headers.append('Authorization', `Basic ${btoa(`${authConfig.user}:${authConfig.password}`)}`);
    } else {
        headers.append(preAuthHeader, authConfig.preAuthenticatedHeaderValue);
    }

    return headers;
}