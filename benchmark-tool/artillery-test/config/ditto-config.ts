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

export interface DittoProtocolConfig {
    http: string;
    ws: string;
}

export interface DittoAuthConfig {
    preAuthenticatedHeaderValue: string;
    user: string;
    password: string;
    devopsUser: string;
    devopsPassword: string;
}

export interface DittoConfig {
    baseUri: string;
    protocol: DittoProtocolConfig;
    auth: DittoAuthConfig;
    /** HTTP client timeout in milliseconds */
    httpTimeoutMs: number;
}

export function getDefaultDittoConfig(): DittoConfig {
    return {
        baseUri: 'localhost:8080',
        protocol: {
            http: 'http',
            ws: 'ws'
        },
        auth: {
            preAuthenticatedHeaderValue: 'nginx:ditto',
            user: '',
            password: '',
            devopsUser: 'devops',
            devopsPassword: ''
        },
        httpTimeoutMs: 5000
    };
}
