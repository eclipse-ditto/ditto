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

import * as fs from 'fs';
import * as path from 'path';
import { parse as parseYaml } from 'yaml';
import { createDefu } from 'defu';
import { TestConfig, TestConfigData, getDefaultTestConfig } from './config';

import {
    CHANNEL_HTTP,
    CHANNEL_KAFKA,
    CHANNEL_WEBSOCKET_LIVE,
    CHANNEL_WEBSOCKET_TWIN,
    ChannelName,
    DEVICE_LIVE_MESSAGES,
    MODIFY_THINGS,
    READ_THINGS,
    SEARCH_THINGS,
    ScenarioName,
    WARMUP
} from './config/scenarios-config';

import { debugLog } from './utils';

// ============================================================================
// Configuration Loading
// ============================================================================

/**
 * Apply environment variable overrides to the config.
 * Environment variables take precedence over YAML config values.
 */
function applyEnvOverrides(config: TestConfigData): void {
    // Ditto
    if (process.env.DITTO_BASE_URI) config.ditto.baseUri = process.env.DITTO_BASE_URI;
    if (process.env.DITTO_HTTP_PROTOCOL) config.ditto.protocol.http = process.env.DITTO_HTTP_PROTOCOL;
    if (process.env.DITTO_WS_PROTOCOL) config.ditto.protocol.ws = process.env.DITTO_WS_PROTOCOL;
    if (process.env.DITTO_PRE_AUTHENTICATED_HEADER_VALUE) config.ditto.auth.preAuthenticatedHeaderValue = process.env.DITTO_PRE_AUTHENTICATED_HEADER_VALUE;
    if (process.env.DITTO_USER) config.ditto.auth.user = process.env.DITTO_USER;
    if (process.env.DITTO_PASSWORD) config.ditto.auth.password = process.env.DITTO_PASSWORD;
    if (process.env.DITTO_DEVOPS_USER) config.ditto.auth.devopsUser = process.env.DITTO_DEVOPS_USER;
    if (process.env.DITTO_DEVOPS_PASSWORD) config.ditto.auth.devopsPassword = process.env.DITTO_DEVOPS_PASSWORD;
    if (process.env.DITTO_HTTP_TIMEOUT_MS) config.ditto.httpTimeoutMs = parseInt(process.env.DITTO_HTTP_TIMEOUT_MS) || config.ditto.httpTimeoutMs;

    // Things
    if (process.env.THINGS_COUNT) config.things.count = parseInt(process.env.THINGS_COUNT) || config.things.count;
    if (process.env.THINGS_START_INDEX) config.things.startIndex = parseInt(process.env.THINGS_START_INDEX) || config.things.startIndex;
    if (process.env.DEVICE_ID_TEMPLATE) config.things.idTemplate = process.env.DEVICE_ID_TEMPLATE;
    if (process.env.DEVICE_NAMESPACE) config.things.namespace = process.env.DEVICE_NAMESPACE;
    if (process.env.CREATE_THINGS) config.things.createBeforeTest = process.env.CREATE_THINGS === '1';
    if (process.env.CREATE_THINGS_BATCH_SIZE) config.things.createBatchSize = parseInt(process.env.CREATE_THINGS_BATCH_SIZE) || config.things.createBatchSize;
    if (process.env.DELETE_THINGS) config.things.deleteAfterTest = process.env.DELETE_THINGS === '1';

    // Connections
    if (process.env.CREATE_DITTO_CONNECTIONS) config.connections.createBeforeTest = process.env.CREATE_DITTO_CONNECTIONS === '1';
    if (process.env.DELETE_DITTO_CONNECTIONS) config.connections.deleteAfterTest = process.env.DELETE_DITTO_CONNECTIONS === '1';
    if (process.env.CONNECTION_OPEN_MAX_RETRIES) config.connections.openMaxRetries = parseInt(process.env.CONNECTION_OPEN_MAX_RETRIES) || config.connections.openMaxRetries;

    // Pre-created connection IDs
    if (process.env.HTTP_PUSH_CONNECTION_ID) config.connections.preCreatedIds.httpPush = process.env.HTTP_PUSH_CONNECTION_ID;
    if (process.env.KAFKA_SOURCE_CONNECTION_ID) config.connections.preCreatedIds.kafkaSource = process.env.KAFKA_SOURCE_CONNECTION_ID;
    if (process.env.KAFKA_TARGET_CONNECTION_ID) config.connections.preCreatedIds.kafkaTarget = process.env.KAFKA_TARGET_CONNECTION_ID;

    // Kafka connection
    if (process.env.KAFKA_BOOTSTRAP_SERVERS) config.connections.kafka.bootstrapServers = process.env.KAFKA_BOOTSTRAP_SERVERS;
    if (process.env.KAFKA_CONNECTION_CONSUMER_CONSUMER_COUNT) config.connections.kafka.consumerCount = parseInt(process.env.KAFKA_CONNECTION_CONSUMER_CONSUMER_COUNT) || config.connections.kafka.consumerCount;
    if (process.env.KAFKA_CONNECTION_PROCESSOR_POOL_SIZE) config.connections.kafka.processorPoolSize = parseInt(process.env.KAFKA_CONNECTION_PROCESSOR_POOL_SIZE) || config.connections.kafka.processorPoolSize;
    if (process.env.KAFKA_CONNECTION_QOS) config.connections.kafka.qos = parseInt(process.env.KAFKA_CONNECTION_QOS) || config.connections.kafka.qos;
    if (process.env.KAFKA_CONNECTION_CUSTOM_ACK) config.connections.kafka.customAck = process.env.KAFKA_CONNECTION_CUSTOM_ACK;
    if (process.env.KAFKA_SOURCE_CONNECTION_CLIENT_COUNT) config.connections.kafka.sourceClientCount = parseInt(process.env.KAFKA_SOURCE_CONNECTION_CLIENT_COUNT) || config.connections.kafka.sourceClientCount;
    if (process.env.KAFKA_TARGET_CONNECTION_CLIENT_COUNT) config.connections.kafka.targetClientCount = parseInt(process.env.KAFKA_TARGET_CONNECTION_CLIENT_COUNT) || config.connections.kafka.targetClientCount;
    if (process.env.KAFKA_SOURCE_CONNECTION_NAME) config.connections.kafka.sourceConnectionName = process.env.KAFKA_SOURCE_CONNECTION_NAME;
    if (process.env.KAFKA_TARGET_CONNECTION_NAME) config.connections.kafka.targetConnectionName = process.env.KAFKA_TARGET_CONNECTION_NAME;

    // Kafka topics
    if (process.env.CREATE_UPDATE_THING_CONSUMER_GROUP_ID) config.connections.kafka.topics.createUpdateThing.consumerGroupId = process.env.CREATE_UPDATE_THING_CONSUMER_GROUP_ID;
    if (process.env.CREATE_UPDATE_THING_CONSUMER_MAX_WAIT_DURATION) config.connections.kafka.topics.createUpdateThing.maxWaitDuration = process.env.CREATE_UPDATE_THING_CONSUMER_MAX_WAIT_DURATION;
    if (process.env.CREATE_UPDATE_THING_SOURCE_TOPIC) config.connections.kafka.topics.createUpdateThing.source.name = process.env.CREATE_UPDATE_THING_SOURCE_TOPIC;
    if (process.env.CREATE_UPDATE_THING_SOURCE_TOPIC_PARTITIONS) config.connections.kafka.topics.createUpdateThing.source.partitions = parseInt(process.env.CREATE_UPDATE_THING_SOURCE_TOPIC_PARTITIONS) || config.connections.kafka.topics.createUpdateThing.source.partitions;
    if (process.env.CREATE_UPDATE_THING_REPLY_TOPIC) config.connections.kafka.topics.createUpdateThing.reply.name = process.env.CREATE_UPDATE_THING_REPLY_TOPIC;
    if (process.env.CREATE_UPDATE_THING_REPLY_TOPIC_PARTITIONS) config.connections.kafka.topics.createUpdateThing.reply.partitions = parseInt(process.env.CREATE_UPDATE_THING_REPLY_TOPIC_PARTITIONS) || config.connections.kafka.topics.createUpdateThing.reply.partitions;
    if (process.env.DELETE_CREATE_UPDATE_THING_TOPICS) config.connections.kafka.topics.createUpdateThing.deleteAfterTest = process.env.DELETE_CREATE_UPDATE_THING_TOPICS === '1';

    // Kafka logging
    if (process.env.KAFKA_CONSUMER_LOGGER_ENABLED) config.connections.kafka.logging.consumer = process.env.KAFKA_CONSUMER_LOGGER_ENABLED === '1';
    if (process.env.KAFKA_PRODUCER_LOGGER_ENABLED) config.connections.kafka.logging.producer = process.env.KAFKA_PRODUCER_LOGGER_ENABLED === '1';

    // HTTP Push connection
    if (process.env.HTTP_PUSH_CONNECTION_CLIENT_COUNT) config.connections.httpPush.clientCount = parseInt(process.env.HTTP_PUSH_CONNECTION_CLIENT_COUNT) || config.connections.httpPush.clientCount;
    if (process.env.HTTP_PUSH_CONNECTION_PARALLELISM) config.connections.httpPush.parallelism = parseInt(process.env.HTTP_PUSH_CONNECTION_PARALLELISM) || config.connections.httpPush.parallelism;
    if (process.env.HTTP_PUSH_URI) config.connections.httpPush.uri = process.env.HTTP_PUSH_URI;

    // Artillery
    if (process.env.ARTILLERY_DURATION) config.artillery.duration = process.env.ARTILLERY_DURATION;
    if (process.env.ARTILLERY_ARRIVAL_RATE) config.artillery.arrivalRate = parseInt(process.env.ARTILLERY_ARRIVAL_RATE) || config.artillery.arrivalRate;

    // Warmup
    if (process.env.RUN_WARMUP) config.warmup.enabled = process.env.RUN_WARMUP === '1';
    if (process.env.THINGS_WARMUP_MAX_DURATION) config.warmup.maxDuration = process.env.THINGS_WARMUP_MAX_DURATION;
    if (process.env.THINGS_WARMUP_BATCH_SIZE) config.warmup.batchSize = parseInt(process.env.THINGS_WARMUP_BATCH_SIZE) || config.warmup.batchSize;

    // Cleanup only mode
    if (process.env.CLEANUP_ONLY) config.cleanupOnly = process.env.CLEANUP_ONLY === '1';

    // Scenarios override: empty = cleanup-only, comma-separated = enable those only
    if (process.env.SCENARIOS_TO_RUN !== undefined) {
        const scenariosToRun = process.env.SCENARIOS_TO_RUN.trim();
        if (scenariosToRun === '') {
            config.scenarios.forEach(s => s.enabled = false);
        } else {
            const enabled = scenariosToRun.split(',').map(s => s.trim()) as ScenarioName[];
            config.scenarios.forEach(s => s.enabled = enabled.includes(s.name));
        }
    }

    // Channel weights: <SCENARIO>_<CHANNEL>_WEIGHT (e.g., MODIFY_THINGS_WEBSOCKET_TWIN_WEIGHT)
    // Scenario timeouts: <SCENARIO>_TIMEOUT (e.g., MODIFY_THINGS_TIMEOUT)
    const toEnvCase = (s: string) => s.replace(/([A-Z])/g, '_$1').toUpperCase();
    for (const scenario of config.scenarios) {
        // Channel weights
        for (const channel of scenario.channels) {
            const envVar = `${toEnvCase(scenario.name)}_${toEnvCase(channel.name)}_WEIGHT`;
            if (process.env[envVar] !== undefined) {
                channel.weight = parseInt(process.env[envVar]!) || 0;
            }
        }
        // Scenario timeout
        const timeoutEnvVar = `${toEnvCase(scenario.name)}_TIMEOUT`;
        if (process.env[timeoutEnvVar] !== undefined) {
            scenario.timeout = parseInt(process.env[timeoutEnvVar]!) || scenario.timeout;
        }
    }
}

/**
 * Load test configuration from YAML file with environment variable overrides.
 * If config file exists, it completely replaces default config.
 * If config file doesn't exist, default config is used.
 * Environment variables are applied as overrides on top.
 *
 * @param configPath - Path to YAML config file (default: CONFIG_PATH env var or ./config.yml)
 * @returns TestConfig instance
 */
export function loadConfig(configPath?: string): TestConfig {
    const filePath = configPath || process.env.CONFIG_PATH || './config.yml';
    const absolutePath = path.isAbsolute(filePath) ? filePath : path.resolve(process.cwd(), filePath);

    let config: TestConfigData;
    const defaults = getDefaultTestConfig();

    if (fs.existsSync(absolutePath)) {
        try {
            const fileContent = fs.readFileSync(absolutePath, 'utf8');
            const yamlConfig = parseYaml(fileContent) as TestConfigData;
            const customArrayMerger = createDefu((obj, key, value) => {
                // by default defu will concat the arrays
                if (Array.isArray(obj[key]) && Array.isArray(value)) {
                   // keep original value
                   obj[key] = value;
                   return true;
                }
            });
            config = customArrayMerger(yamlConfig, defaults) as TestConfigData;
            debugLog(`[Config] Loaded configuration from: ${absolutePath}`);
        } catch (error) {
            console.error(`[Config] Failed to parse YAML config file: ${absolutePath}`, error);
            process.exit(1);
        }
    } else {
        console.log(`[Config] Config file not found: ${absolutePath}. Using defaults.`);
        config = defaults;
    }

    // Apply environment variable overrides
    applyEnvOverrides(config);

    return new TestConfig(config);
}

// Global config instance (lazy loaded)
let _config: TestConfig | null = null;

/**
 * Get the global config instance.
 * Loads config on first access.
 */
export function getConfig(): TestConfig {
    if (!_config) {
        _config = loadConfig();
    }
    return structuredClone(_config);
}

// ============================================================================
// Constants (hardcoded, not config-dependent)
// ============================================================================

// Device feature constants (used in thing creation templates)
export const DEVICE_FEATURE_NAME: string = 'coffee-brewer';
export const DEVICE_FEATURE_PROPERTY: string = 'brewed-coffees';

// Kafka message header constants
export const DEVICE_ID_HEADER: string = 'device_id';
export const CREATE_THING_HEADER: string = 'create_thing';
export const DITTO_MESSAGE_HEADER: string = 'ditto_message';

// All available channels (for iteration/validation)
export const ALL_CHANNELS = [CHANNEL_HTTP, CHANNEL_WEBSOCKET_TWIN, CHANNEL_WEBSOCKET_LIVE, CHANNEL_KAFKA] as const;

// Operation name constants (used for metrics naming)
export const OPERATION_GET_THING = 'getThing';
export const OPERATION_GET_THINGS_BATCH = 'getThingsBatch';
export const OPERATION_SEARCH_THINGS = 'searchThings';
export const OPERATION_MODIFY_THING = 'modifyThing';
export const OPERATION_SEND_LIVE_MESSAGE = 'sendLiveMessage';

// Scenario channel support - which channels each scenario supports
export const SCENARIO_SUPPORTED_CHANNELS: Record<ScenarioName, ChannelName[]> = {
    [WARMUP]: [CHANNEL_HTTP],
    [READ_THINGS]: [CHANNEL_HTTP],
    [SEARCH_THINGS]: [CHANNEL_HTTP],
    [MODIFY_THINGS]: [CHANNEL_HTTP, CHANNEL_WEBSOCKET_TWIN, CHANNEL_KAFKA],
    [DEVICE_LIVE_MESSAGES]: [CHANNEL_HTTP, CHANNEL_WEBSOCKET_LIVE]
};
