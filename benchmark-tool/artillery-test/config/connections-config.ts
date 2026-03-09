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

import { KafkaConnectionConfig, getDefaultKafkaConnectionConfig } from './kafka-connection-config';
import { HttpPushConnectionConfig, getDefaultHttpPushConnectionConfig } from './http-connection-config';

export interface PreCreatedConnectionIds {
    httpPush: string | null;
    kafkaSource: string | null;
    kafkaTarget: string | null;
}

export interface ConnectionsConfig {
    createBeforeTest: boolean;
    deleteAfterTest: boolean;
    openMaxRetries: number;
    preCreatedIds: PreCreatedConnectionIds;
    kafka: KafkaConnectionConfig;
    httpPush: HttpPushConnectionConfig;
}

export function getDefaultConnectionsConfig(): ConnectionsConfig {
    return {
        createBeforeTest: false,
        deleteAfterTest: false,
        openMaxRetries: 10,
        preCreatedIds: {
            httpPush: null,
            kafkaSource: null,
            kafkaTarget: null
        },
        kafka: getDefaultKafkaConnectionConfig(),
        httpPush: getDefaultHttpPushConnectionConfig()
    };
}
