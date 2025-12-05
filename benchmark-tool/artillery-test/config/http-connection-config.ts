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

export interface HttpPushTargetConfig {
    address: string;
    topics: string[];
    issuedAcknowledgementLabel: string;
}

export interface HttpPushConnectionConfig {
    clientCount: number;
    parallelism: number;
    uri: string;
    targets: HttpPushTargetConfig[];
}

export function getDefaultHttpPushConnectionConfig(): HttpPushConnectionConfig {
    return {
        clientCount: 1,
        parallelism: 1,
        uri: 'http://localhost:8081',
        targets: [
            {
                address: 'POST:/{{ thing:id }}',
                topics: ["_/_/things/twin/events?filter=eq(topic:action,'modified')"],
                issuedAcknowledgementLabel: ''
            },
            {
                address: 'POST:/live_message',
                topics: ['_/_/things/live/messages'],
                issuedAcknowledgementLabel: 'live-response'
            }
        ]
    };
}
