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

import { getConfig } from './common';
import * as httpUtil from './http-util';
import * as kafkaUtil from './kafka-util';
import { logManualCleanupInstructions } from './cleanup-util';
import { DEVICE_LIVE_MESSAGES, getConfigEnabledScenarios, getThingId, MODIFY_THINGS, TestConfig } from './config';
import { debugLog } from './utils';
import { WebSocketChannel } from './channels/websocket-channel';

/**
 * TestSetup - Manages resource lifecycle for Artillery tests
 * Handles: connections, things creation/deletion, Kafka topics, cleanup
 */
export class TestSetup {
    readonly config: TestConfig = getConfig();
    httpPushConnectionId: string | null = null;
    kafkaSourceConnectionId: string | null = null;
    kafkaTargetConnectionId: string | null = null;
    httpPushConnectionCreated: boolean = false;
    kafkaSourceConnectionCreated: boolean = false;
    kafkaTargetConnectionCreated: boolean = false;
    cleanupCalled: boolean = false;
    public cleanupOnly: boolean = false;

    createThingsInitiated = false;
    thingsDeleted = false;
    connectionsDeleted = false;

    public constructor(cleanupOnly: boolean = false) {
        this.cleanupOnly = cleanupOnly;
        if (cleanupOnly) {
            this.httpPushConnectionId = this.config.connections.preCreatedIds.httpPush || null;
            this.httpPushConnectionCreated = true;
            this.kafkaSourceConnectionId = this.config.connections.preCreatedIds.kafkaSource || null;
            this.kafkaSourceConnectionCreated = true;
            this.kafkaTargetConnectionId = this.config.connections.preCreatedIds.kafkaTarget || null;
            this.kafkaTargetConnectionCreated = true;
        }
    }

    /**
     * Setup Ditto connections
     */
    async setupConnections() {
        console.log('\n[Setup] Configuring Ditto connections...');

        const enabledScenarios = getConfigEnabledScenarios(this.config);
        const scenarioNames = enabledScenarios.map(s => s.name);
        console.log(`[Setup] Scenarios to set up: ${scenarioNames}`);
        const needsHttpPush = scenarioNames.includes(DEVICE_LIVE_MESSAGES) || scenarioNames.includes(MODIFY_THINGS);
        const needsKafkaSource = scenarioNames.includes(MODIFY_THINGS) || this.config.things.createBeforeTest;

        if (needsHttpPush && !this.httpPushConnectionId) {
            if (this.config.connections.preCreatedIds.httpPush) {
                console.log(`[Ditto] Using existing HTTP Push connection: ${this.config.connections.preCreatedIds.httpPush}`);
                this.httpPushConnectionId = this.config.connections.preCreatedIds.httpPush;
            } else {
                const httpConn = await httpUtil.createHttpPushConnection();
                this.httpPushConnectionId = httpConn.id;
                this.httpPushConnectionCreated = true;
            }

            if (this.httpPushConnectionId) {
                await httpUtil.waitForConnectionToOpen(this.httpPushConnectionId);
            }
        }

        if (needsKafkaSource && !this.kafkaSourceConnectionId) {
            if (this.config.connections.preCreatedIds.kafkaSource) {
                console.log(`[Ditto] Using existing Kafka Source connection: ${this.config.connections.preCreatedIds.kafkaSource}`);
                this.kafkaSourceConnectionId = this.config.connections.preCreatedIds.kafkaSource;
            } else {
                const kafkaConn = await httpUtil.createKafkaSourceConnection();
                this.kafkaSourceConnectionId = kafkaConn.id;
                this.kafkaSourceConnectionCreated = true;
            }

            if (this.kafkaSourceConnectionId) {
                await httpUtil.waitForConnectionToOpen(this.kafkaSourceConnectionId);
            }
        }

        console.log('[Setup] Connections configured');
    }

    /**
     * Create things via Kafka
     */
    async createThings() {
        if (!this.config.things.createBeforeTest) {
            console.log('[Setup] Thing creation disabled');
            return;
        }

        console.log('\n[Setup] Creating things...');

        await kafkaUtil.createThingsTopicsIfNotAvailable();

        if (this.config.connections.preCreatedIds.kafkaTarget) {
            console.log(`[Ditto] Using existing Kafka Target connection: ${this.config.connections.preCreatedIds.kafkaTarget}`);
            this.kafkaTargetConnectionId = this.config.connections.preCreatedIds.kafkaTarget;
            await httpUtil.waitForConnectionToOpen(this.kafkaTargetConnectionId);
        } else {
            const kafkaTargetConn = await httpUtil.createKafkaTargetConnection();
            this.kafkaTargetConnectionId = kafkaTargetConn.id;
            this.kafkaTargetConnectionCreated = true;
            if (this.kafkaTargetConnectionId) {
                await httpUtil.waitForConnectionToOpen(this.kafkaTargetConnectionId);
            }
        }

        await kafkaUtil.initializeProducer();
        await kafkaUtil.initializeConsumer();

        this.createThingsInitiated = true;
        await kafkaUtil.createThingsAndPolicies(this.config.things.count);

        console.log(`[Setup] Created ${this.config.things.count} things`);
    }

    private async deleteThings(ignoreNotFound?: boolean): Promise<boolean> {

        if (!this.config.things.deleteAfterTest || this.config.things.count === 0) {
            return true;
        }

        console.log(`[Cleanup] Deleting ${this.config.things.count} things...`);

        for (let i = 0; i < this.config.things.count; i++) {
            try {
                const thingId = getThingId(this.config, i);
                await httpUtil.deleteThing(thingId);
                await httpUtil.deletePolicy(thingId);
            } catch (err: any) {
                if (ignoreNotFound && err._status === 404) {
                    debugLog('[Cleanup] IGNORE Failed to delete things:', err._value.message);
                } else {
                    console.error('[Cleanup] Failed to delete things:', err);
                    throw err;
                }
            }
        }
        console.log('[Cleanup] Things deleted');
        return true;

    }

    private async deleteConnections(ignoreNotFound?: boolean): Promise<boolean> {
        if (!this.config.connections.deleteAfterTest) {
            return true;
        }

        console.log('[Cleanup] Deleting connections...');

        try {
            if (this.httpPushConnectionId && this.httpPushConnectionCreated) {
                await httpUtil.deleteConnection(this.httpPushConnectionId);
                console.log('[Cleanup] HTTP Push connection deleted');
            }

            if (this.kafkaSourceConnectionId && this.kafkaSourceConnectionCreated) {
                await httpUtil.deleteConnection(this.kafkaSourceConnectionId);
                console.log('[Cleanup] Kafka Source connection deleted');
            }

            if (this.kafkaTargetConnectionId && this.kafkaTargetConnectionCreated) {
                await httpUtil.deleteConnection(this.kafkaTargetConnectionId);
                console.log('[Cleanup] Kafka Target connection deleted');
            }

            return true;
        } catch (err: any) {
            console.error('[Cleanup] Failed to delete connections:', err);
            if (ignoreNotFound && err._status === 404) {
                return true;
            }
            throw err;
        }
    }

    private async deleteKafkaTopics() {
        if (!this.config.connections.kafka.topics.createUpdateThing.deleteAfterTest) {
            return;
        }

        console.log('[Cleanup] Deleting Kafka topics...');
        try {
            await kafkaUtil.deleteTopics();
            console.log('[Cleanup] Kafka topics deleted');
        } catch (err: any) {
            console.error('[Cleanup] Error deleting Kafka topics:', err.message);
            throw err;
        }
    }

    /**
     * Perform complete cleanup (runs only once)
     */
    async cleanup(alreadyLoggedCleanupInfo: boolean = false) {
        if (this.cleanupCalled) {
            return;
        }
        this.cleanupCalled = true;

        this.thingsDeleted = false;
        this.connectionsDeleted = false;

        WebSocketChannel.resetInstance();

        try {
            if (this.createThingsInitiated) {
                this.thingsDeleted = await this.deleteThings(this.cleanupOnly);
            }
            this.connectionsDeleted = await this.deleteConnections(this.cleanupOnly);
            // Stop producer/consumer first to prevent fetch errors on deleted topics
            await kafkaUtil.stopProducerAndConsumer();
            // Delete topics (admin client still connected)
            await this.deleteKafkaTopics();
            // Disconnect admin client
            await kafkaUtil.disconnectClients();
            console.log('=== CLEANUP COMPLETE ===');
        } catch (error: any) {
            // Ditto error messages are included in the _value field.
            console.error('[Cleanup] Cleanup was interrupted or failed:', error);
            if (!alreadyLoggedCleanupInfo) {
                console.error('');
                logManualCleanupInstructions(
                    this.thingsDeleted, this.connectionsDeleted,
                    this.config.things.count,
                    this.kafkaSourceConnectionCreated ? this.kafkaSourceConnectionId : null,
                    this.kafkaTargetConnectionCreated ? this.kafkaTargetConnectionId : null,
                    this.httpPushConnectionCreated ? this.httpPushConnectionId : null
                );
            }
        }
    }

    logManualCleanupInstructions(alreadyLoggedCleanupInfo: boolean = false) {
        if (!alreadyLoggedCleanupInfo) {
            console.error('');
            logManualCleanupInstructions(
                this.thingsDeleted, this.connectionsDeleted,
                this.config.things.count,
                this.kafkaSourceConnectionCreated ? this.kafkaSourceConnectionId : null,
                this.kafkaTargetConnectionCreated ? this.kafkaTargetConnectionId : null,
                this.httpPushConnectionCreated ? this.httpPushConnectionId : null
            );
        }
    }
}
