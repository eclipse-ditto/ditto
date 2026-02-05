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

export interface KafkaTopicConfig {
    name: string;
    partitions: number;
}

export interface KafkaTopicsCreateUpdateThingConfig {
    consumerGroupId: string;
    maxWaitDuration: string;
    source: KafkaTopicConfig;
    reply: KafkaTopicConfig;
    deleteAfterTest: boolean;
}

export interface KafkaTopicsConfig {
    createUpdateThing: KafkaTopicsCreateUpdateThingConfig;
}

export interface KafkaLoggingConfig {
    consumer: boolean;
    producer: boolean;
}

export interface KafkaConnectionConfig {
    bootstrapServers: string;
    consumerCount: number;
    processorPoolSize: number;
    qos: number;
    customAck: string;
    sourceClientCount: number;
    targetClientCount: number;
    sourceConnectionName: string;
    targetConnectionName: string;
    topics: KafkaTopicsConfig;
    logging: KafkaLoggingConfig;
}

export function getDefaultKafkaConnectionConfig(): KafkaConnectionConfig {
    return {
        bootstrapServers: 'localhost:9092',
        consumerCount: 1,
        processorPoolSize: 1,
        qos: 0,
        customAck: '',
        sourceClientCount: 1,
        targetClientCount: 1,
        sourceConnectionName: 'kafka-source',
        targetConnectionName: 'kafka-target',
        topics: {
            createUpdateThing: {
                consumerGroupId: 'group-create-update',
                maxWaitDuration: '30s',
                source: {
                    name: 'create-update',
                    partitions: 2
                },
                reply: {
                    name: 'create-update-reply',
                    partitions: 2
                },
                deleteAfterTest: false
            }
        },
        logging: {
            consumer: false,
            producer: false
        }
    };
}
