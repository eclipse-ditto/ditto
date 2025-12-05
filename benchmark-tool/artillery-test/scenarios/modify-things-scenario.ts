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

import { Scenario, ThingModifier } from '../interfaces';
import {
    getConfig,
    CHANNEL_HTTP,
    CHANNEL_WEBSOCKET_TWIN,
    CHANNEL_KAFKA,
    DEVICE_FEATURE_NAME,
    DEVICE_FEATURE_PROPERTY,
    OPERATION_MODIFY_THING,
    MODIFY_THINGS
} from '../common';
import { executeWithMetrics } from './scenario-utils';
import { HttpChannel } from '../channels/http-channel';
import { WebSocketChannel } from '../channels/websocket-channel';
import { KafkaChannel } from '../channels/kafka-channel';
import { getRandomThingId, getScenarioTimeout } from '../config';

/**
 * MODIFY_THINGS scenario - modify a random thing
 * Supports: HTTP, WEBSOCKET_TWIN, KAFKA
 */
export const modifyThingsScenario: Scenario = {
    async execute(context: any, events: any): Promise<void> {
        const config = getConfig();
        const channel = context.vars?.channel || CHANNEL_HTTP;
        const thingId = getRandomThingId(config);
        const timeout = getScenarioTimeout(config, MODIFY_THINGS);

        try {
            let channelClient: ThingModifier;

            switch (channel) {
                case CHANNEL_HTTP:
                    channelClient = new HttpChannel();
                    break;
                case CHANNEL_WEBSOCKET_TWIN:
                    const wsChannel = WebSocketChannel.getInstance();
                    await wsChannel.getTwinClient();
                    channelClient = wsChannel;
                    break;
                case CHANNEL_KAFKA:
                    channelClient = new KafkaChannel();
                    break;
                default:
                    throw new Error(`Unsupported channel for MODIFY_THINGS: ${channel}`);
            }

            await executeWithMetrics(
                () => channelClient.modifyThing({
                    thingId,
                    updates: {
                        path: `${DEVICE_FEATURE_NAME}/properties/${DEVICE_FEATURE_PROPERTY}`,
                        value: Math.floor(Math.random() * 1000)
                    },
                    timeout
                }),
                OPERATION_MODIFY_THING,
                events,
                context
            );
        } catch (error: any) {
            console.error(`[Scenario: ${context.vars.scenarioName}] Failed to modify thing ${thingId} via ${channel}:`, error);
        }
    }
};
