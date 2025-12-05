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

import { Scenario, LiveMessenger } from '../interfaces';
import {
    getConfig,
    CHANNEL_HTTP,
    CHANNEL_WEBSOCKET_LIVE,
    OPERATION_SEND_LIVE_MESSAGE,
    DEVICE_LIVE_MESSAGES
} from '../common';
import { executeWithMetrics } from './scenario-utils';
import { HttpChannel } from '../channels/http-channel';
import { WebSocketChannel } from '../channels/websocket-channel';
import { GenericResponse } from '@eclipse-ditto/ditto-javascript-client-node';
import { getRandomThingId, getScenarioTimeout } from '../config';

/**
 * DEVICE_LIVE_MESSAGES scenario - send a live message to a random thing
 * Supports: HTTP, WEBSOCKET_LIVE
 */
export const deviceLiveMessagesScenario: Scenario = {
    async execute(context: any, events: any): Promise<void> {
        const config = getConfig();
        const channelType = context.vars?.channel || CHANNEL_HTTP;
        const thingId = getRandomThingId(config);
        const timeout = getScenarioTimeout(config, DEVICE_LIVE_MESSAGES);

        try {
            let channelClient: LiveMessenger;

            switch (channelType) {
                case CHANNEL_HTTP:
                    channelClient = new HttpChannel();
                    break;
                case CHANNEL_WEBSOCKET_LIVE:
                    const wsChannel = WebSocketChannel.getInstance();
                    await wsChannel.getLiveClient();
                    channelClient = wsChannel;
                    break;
                default:
                    throw new Error(`Unsupported channel for DEVICE_LIVE_MESSAGES: ${channelType}`);
            }

            await executeWithMetrics(
                async () => {
                    let response: GenericResponse = await channelClient.sendLiveMessage({
                        thingId,
                        subject: 'test-subject',
                        payload: { message: 'test payload', timestamp: Date.now() },
                        timeout
                    });
                    if (response.status >= 300) {
                        throw new Error(`Status: ${response.status} Body: ${JSON.stringify(response.body)}`);
                    }
                },
                OPERATION_SEND_LIVE_MESSAGE,
                events,
                context
            );
        } catch (error: any) {
            console.error(`[Scenario: ${context.vars.scenarioName}] Failed to send live message to ${thingId} via ${channelType}:`, error);
        }
    }
};