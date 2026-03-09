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

import { Kafka, Producer, Consumer, Admin, logLevel, Partitioners, KafkaMessage } from 'kafkajs';
import {
    getConfig,
    DEVICE_ID_HEADER,
    CREATE_THING_HEADER,
    DITTO_MESSAGE_HEADER
} from './common';
import { randomUUID } from 'crypto';
import { debugLog, parseDuration } from './utils';
import { getKafkaBootstrapServers, getThingId } from './config';

const DITTO_COMMANDS_MODIFY_TOPIC_PATH = 'things/twin/commands/modify';
const THING_PERSISTED_ACK = 'twin-persisted';

interface BatchResolver {
    thingIds: Set<string>;
    resolve: () => void;
    reject: (error: Error) => void;
    timeout: NodeJS.Timeout;
}

let kafka: Kafka | undefined;
let producer: Producer | undefined;
let consumer: Consumer | undefined;
let admin: Admin | undefined;
let consumerRunning = false;
let currentConsumerGroupId: string | null = null;
let currentBatchResolvers = new Map<number, BatchResolver>();
let interrupted = false;

/**
 * Set interrupt flag to stop ongoing thing creation
 */
function setInterrupted(): void {
    interrupted = true;
    // Reject any pending batch resolvers
    for (const [batchId, resolver] of currentBatchResolvers.entries()) {
        clearTimeout(resolver.timeout);
        resolver.reject(new Error('Interrupted by signal'));
    }
    currentBatchResolvers.clear();
}

/**
 * Check if interrupted
 */
function isInterrupted(): boolean {
    return interrupted;
}

function getKafkaClient(): Kafka {
    if (!kafka) {
        const config = getConfig();
        const brokers = getKafkaBootstrapServers(config);
        debugLog(`[Kafka] Creating client with brokers: ${JSON.stringify(brokers)}`);
        kafka = new Kafka({
            clientId: 'ditto-benchmark',
            brokers: brokers,
            logLevel: getConfig().connections.kafka.logging.producer ? logLevel.WARN : logLevel.INFO
        });
    }
    return kafka;
}

async function initializeProducer(): Promise<Producer> {
    if (!producer) {
        producer = getKafkaClient().producer({
            createPartitioner: Partitioners.DefaultPartitioner
        });
        await producer.connect();
        debugLog('[Kafka] Producer connected');
    }
    return producer;
}

async function initializeConsumer(): Promise<Consumer> {
    if (!consumer) {
        const kafkaTopicsConfig = getConfig().connections.kafka.topics.createUpdateThing;
        const uniqueGroupId = `${kafkaTopicsConfig.consumerGroupId}-${randomUUID()}`;
        currentConsumerGroupId = uniqueGroupId;

        debugLog(`[Kafka] Initializing consumer, group: ${uniqueGroupId}, topic: ${kafkaTopicsConfig.reply.name}`);

        consumer = getKafkaClient().consumer({
            groupId: uniqueGroupId
        });

        await consumer.connect();
        await consumer.subscribe({
            topic: kafkaTopicsConfig.reply.name,
            fromBeginning: false
        });
        debugLog('[Kafka] Consumer connected and subscribed');
    }
    return consumer;
}

/**
 * Wait for Kafka consumer to be fully ready (connected + joined group).
 *
 * Note: The timeouts below are internal safety guards, not configurable operational timeouts.
 * They prevent hanging if Kafka is unreachable and handle kafkajs event timing edge cases.
 */
async function waitForConsumerReady(consumer: Consumer): Promise<void> {
    return new Promise((resolve, reject) => {
        // Safety timeout: fail fast if Kafka is unreachable or group join fails
        const timeout = setTimeout(() => {
            reject(new Error('Timeout waiting for consumer to join group'));
        }, 10000);

        const { CONNECT, GROUP_JOIN } = consumer.events;
        let connected = false;
        let groupJoined = false;

        const checkReady = () => {
            if (connected && groupJoined) {
                clearTimeout(timeout);
                // Brief stabilization delay to ensure consumer is fully ready before use
                setTimeout(() => {
                    debugLog('[Kafka] Consumer ready');
                    resolve();
                }, 500);
            }
        };

        consumer.on(CONNECT, () => {
            connected = true;
            checkReady();
        });

        consumer.on(GROUP_JOIN, () => {
            groupJoined = true;
            checkReady();
        });

        // Fallback: if already connected, GROUP_JOIN event may not fire reliably
        if ((consumer as any).connected) {
            connected = true;
            setTimeout(() => {
                if (!groupJoined) {
                    groupJoined = true;
                    checkReady();
                }
            }, 1000);
        }
    });
}

async function startConsumerIfNeeded(consumer: Consumer): Promise<void> {
    if (consumerRunning) return;

    consumerRunning = true;

    consumer.run({
        eachMessage: async ({ topic, partition, message } : { topic: string, partition: number, message: KafkaMessage }) => {
            try {
                debugLog(`[Kafka] Message from ${topic}:${partition}, offset: ${message.offset}`);

                const event = JSON.parse(message.value!.toString());
                const thingId = event.thingId || event.value?.thingId;

                for (const [batchId, resolver] of currentBatchResolvers.entries()) {
                    if (thingId && resolver.thingIds.has(thingId)) {
                        resolver.thingIds.delete(thingId);

                        debugLog(`[Kafka] Thing created: ${thingId} (batch ${batchId}: ${resolver.thingIds.size} remaining)`);

                        if (resolver.thingIds.size === 0) {
                            clearTimeout(resolver.timeout);
                            currentBatchResolvers.delete(batchId);
                            resolver.resolve();
                        }
                        break;
                    }
                }
            } catch (err: any) {
                console.error('[Kafka] Error parsing message:', err.message);
            }
        }
    }).catch((err: any) => {
        console.error('[Kafka] Consumer error:', err);
        for (const resolver of currentBatchResolvers.values()) {
            clearTimeout(resolver.timeout);
            resolver.reject(err);
        }
        currentBatchResolvers.clear();
        consumerRunning = false;
    });
}

async function getAdminClient(): Promise<Admin> {
    if (!admin) {
        admin = getKafkaClient().admin();
        await admin.connect();
    }
    return admin;
}

async function createThingsTopicsIfNotAvailable(): Promise<void> {
    const adminClient = await getAdminClient();
    const existingTopics = await adminClient.listTopics();
    const kafkaTopicsConfig = getConfig().connections.kafka.topics.createUpdateThing;

    const requiredTopics = [
        { topic: kafkaTopicsConfig.source.name, numPartitions: kafkaTopicsConfig.source.partitions, replicationFactor: 1 },
        { topic: kafkaTopicsConfig.reply.name, numPartitions: kafkaTopicsConfig.reply.partitions, replicationFactor: 1 }
    ];

    const topicsToCreate = requiredTopics.filter(topicConfig => !existingTopics.includes(topicConfig.topic));

    if (topicsToCreate.length === 0) {
        debugLog('[Kafka] All required topics already exist');
        return;
    }

    debugLog(`[Kafka] Creating ${topicsToCreate.length} topics...`);
    await adminClient.createTopics({ topics: topicsToCreate });

    const updatedTopics = await adminClient.listTopics();
    const missingTopics = requiredTopics.filter(topicConfig => !updatedTopics.includes(topicConfig.topic));

    if (missingTopics.length > 0) {
        throw new Error(`Topics not found after creation: ${missingTopics.map(c => c.topic).join(', ')}`);
    }

    debugLog('[Kafka] Topics created and verified');
}

async function deleteTopics(): Promise<void> {
    const adminClient = await getAdminClient();
    const kafkaTopicsConfig = getConfig().connections.kafka.topics.createUpdateThing;

    try {
        await adminClient.deleteTopics({
            topics: [kafkaTopicsConfig.source.name, kafkaTopicsConfig.reply.name]
        });
        debugLog('[Kafka] Topics deleted');
    } catch (err: any) {
        console.error('[Kafka] Error deleting topics:', err.message);
    }
}

async function sendCreateThingsAndPolicies(thingsCount: number): Promise<void> {
    const prod = await initializeProducer();
    const cons = await initializeConsumer();

    await startConsumerIfNeeded(cons);
    await waitForConsumerReady(cons);

    const config = getConfig();
    const batchSize = config.things.createBatchSize;

    debugLog(`[Kafka] Creating ${thingsCount} things (batch size: ${batchSize})...`);

    let batchNum = 0;
    let offset = 0;
    const startTime = Date.now();

    while (offset < thingsCount) {
        if (interrupted) {
            console.log(`[Kafka] Thing creation interrupted at ${offset}/${thingsCount}`);
            throw new Error('Thing creation interrupted by signal');
        }

        const batchEnd = Math.min(offset + batchSize, thingsCount);

        const batchThingIds: string[] = [];
        for (let i = offset; i < batchEnd; i++) {
            batchThingIds.push(getThingId(config, i));
        }

        const consumePromise = registerBatchConsumer(batchNum + 1, new Set(batchThingIds));

        const messages = batchThingIds.map(thingId => ({
            key: thingId,
            value: null,
            headers: {
                [DEVICE_ID_HEADER]: thingId,
                [CREATE_THING_HEADER]: '1'
            }
        }));

        await prod.send({
            topic: config.connections.kafka.topics.createUpdateThing.source.name,
            messages: messages as any
        });

        await consumePromise;

        batchNum++;
        offset = batchEnd;
    }

    console.log(`[Kafka] All ${thingsCount} things created in ${Date.now() - startTime}ms`);
}

function registerBatchConsumer(batchId: number, thingIdsSet: Set<string>): Promise<void> {
    return new Promise((resolve, reject) => {
        const maxWaitDuration = parseDuration(getConfig().connections.kafka.topics.createUpdateThing.maxWaitDuration);
        const timeout = setTimeout(() => {
            console.error(`[Kafka] Timeout for batch ${batchId} (${thingIdsSet.size} things missing)`);
            currentBatchResolvers.delete(batchId);
            reject(new Error(`Timeout waiting for thing creation in batch ${batchId}`));
        }, maxWaitDuration);

        currentBatchResolvers.set(batchId, { thingIds: thingIdsSet, resolve, reject, timeout });
    });
}

async function sendModifyThing(thingId: string, updates: { path: string; value: any }, timeout?: number): Promise<void> {
    const prod = await initializeProducer();
    const config = getConfig();

    const messageHeaders: Record<string, any> = { 'requested-acks': [THING_PERSISTED_ACK] };
    if (timeout !== undefined) {
        messageHeaders.timeout = timeout;
    }

    const message = {
        topic: `${config.things.namespace}/${thingId.split(':')[1]}/${DITTO_COMMANDS_MODIFY_TOPIC_PATH}`,
        headers: messageHeaders,
        path: updates.path,
        value: updates.value
    };

    await prod.send({
        topic: config.connections.kafka.topics.createUpdateThing.source.name,
        messages: [{
            key: thingId,
            value: JSON.stringify(message),
            headers: {
                [DEVICE_ID_HEADER]: thingId,
                [DITTO_MESSAGE_HEADER]: '1'
            }
        }]
    });
}

/**
 * Stop producer and consumer (but keep admin connected for topic deletion)
 */
async function stopProducerAndConsumer(): Promise<void> {
    if (consumer && consumerRunning) {
        try {
            await consumer.stop();
            consumerRunning = false;
            debugLog('[Kafka] Consumer stopped');
        } catch (err: any) {
            console.error('[Kafka] Error stopping consumer:', err.message);
        }
    }

    const disconnectPromises: Promise<void>[] = [];

    if (producer) {
        disconnectPromises.push(
            producer.disconnect()
                .then(() => {
                    debugLog('[Kafka] Producer disconnected');
                    producer = undefined;
                })
                .catch((err: any) => console.error('[Kafka] Error disconnecting producer:', err.message))
        );
    }

    if (consumer) {
        disconnectPromises.push(
            consumer.disconnect()
                .then(() => {
                    debugLog('[Kafka] Consumer disconnected');
                    consumer = undefined;
                })
                .catch((err: any) => console.error('[Kafka] Error disconnecting consumer:', err.message))
        );
    }

    await Promise.all(disconnectPromises);
}

/**
 * Disconnect all Kafka clients including admin
 */
async function disconnectClients(): Promise<void> {
    await stopProducerAndConsumer();

    if (currentConsumerGroupId && admin) {
        try {
            await admin.deleteGroups([currentConsumerGroupId]);
            debugLog('[Kafka] Consumer group deleted');
            currentConsumerGroupId = null;
        } catch (err: any) {
            debugLog(`[Kafka] Could not delete consumer group: ${err.message}`);
        }
    }

    if (admin) {
        try {
            await admin.disconnect();
            debugLog('[Kafka] Admin disconnected');
            admin = undefined;
        } catch (err: any) {
            console.error('[Kafka] Error disconnecting admin:', err.message);
        }
    }
}

export {
    initializeProducer,
    initializeConsumer,
    createThingsTopicsIfNotAvailable,
    deleteTopics,
    sendCreateThingsAndPolicies as createThingsAndPolicies,
    sendModifyThing,
    stopProducerAndConsumer,
    disconnectClients,
    setInterrupted,
    isInterrupted
};
