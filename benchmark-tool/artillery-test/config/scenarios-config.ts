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

// Scenario name constants (camelCase for config)
export const WARMUP = 'warmup';
export const READ_THINGS = 'readThings';
export const SEARCH_THINGS = 'searchThings';
export const MODIFY_THINGS = 'modifyThings';
export const DEVICE_LIVE_MESSAGES = 'deviceLiveMessages';

export type ScenarioName =
  | typeof WARMUP
  | typeof READ_THINGS
  | typeof SEARCH_THINGS
  | typeof MODIFY_THINGS
  | typeof DEVICE_LIVE_MESSAGES;

// Channel name constants (camelCase for config)
export const CHANNEL_HTTP = 'http';
export const CHANNEL_KAFKA = 'kafka';
export const CHANNEL_WEBSOCKET_TWIN = 'websocketTwin';
export const CHANNEL_WEBSOCKET_LIVE = 'websocketLive';

export type ChannelName =
  | typeof CHANNEL_HTTP
  | typeof CHANNEL_KAFKA
  | typeof CHANNEL_WEBSOCKET_TWIN
  | typeof CHANNEL_WEBSOCKET_LIVE

export interface ScenarioChannelWeights {
    name: ChannelName
    weight: number
}

export interface ScenarioConfig {
    name: ScenarioName;
    enabled: boolean;
    channels: ScenarioChannelWeights[];
    /** Timeout in seconds for Ditto operations (used in HTTP query param and Ditto protocol headers) */
    timeout: number;
}


/** Default timeout in seconds for Ditto operations */
export const DEFAULT_SCENARIO_TIMEOUT = 10;

export function getDefaultScenariosConfig(): ScenarioConfig[] {
    return [
        {
            name: READ_THINGS,
            enabled: false,
            channels: [],
            timeout: DEFAULT_SCENARIO_TIMEOUT
        },
        {
            name: SEARCH_THINGS,
            enabled: false,
            channels: [],
            timeout: DEFAULT_SCENARIO_TIMEOUT
        },
        {
            name: MODIFY_THINGS,
            enabled: false,
            channels: [],
            timeout: DEFAULT_SCENARIO_TIMEOUT
        },
        {
            name: DEVICE_LIVE_MESSAGES,
            enabled: false,
            channels: [],
            timeout: DEFAULT_SCENARIO_TIMEOUT
        }
    ];
}
