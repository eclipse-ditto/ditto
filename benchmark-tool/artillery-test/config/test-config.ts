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

import { ArtilleryConfig, getDefaultArtilleryConfig } from './artillery-config';
import { ConnectionsConfig, getDefaultConnectionsConfig } from './connections-config';
import { DittoConfig, getDefaultDittoConfig } from './ditto-config';
import { ScenarioConfig, ScenarioChannelWeights, ScenarioName, getDefaultScenariosConfig, DEFAULT_SCENARIO_TIMEOUT } from './scenarios-config';
import { ThingsConfig, getDefaultThingsConfig } from './things-config';
import { WarmupConfig, getDefaultWarmupConfig } from './warmup-config';

export interface TestConfigData {
    ditto: DittoConfig;
    things: ThingsConfig;
    connections: ConnectionsConfig;
    artillery: ArtilleryConfig;
    scenarios: ScenarioConfig[];
    warmup: WarmupConfig;
    cleanupOnly: boolean;
}

/**
 * Test configuration class with helper methods.
 * Loaded from YAML config file with optional environment variable overrides.
 */
export class TestConfig implements TestConfigData {
    ditto: DittoConfig;
    things: ThingsConfig;
    connections: ConnectionsConfig;
    artillery: ArtilleryConfig;
    scenarios: ScenarioConfig[];
    warmup: WarmupConfig;
    cleanupOnly: boolean;

    constructor(data: TestConfigData) {
        this.ditto = data.ditto;
        this.things = data.things;
        this.connections = data.connections;
        this.artillery = data.artillery;
        this.scenarios = data.scenarios;
        this.warmup = data.warmup;
        this.cleanupOnly = data.cleanupOnly;
    }
}

/**
     * Get the full thing ID for a given index.
     * @param index - Zero-based index
     * @returns Full thing ID (e.g., "org.eclipse.ditto:test-thing-1")
     */
export function getThingId(testConfig: TestConfigData, index: number): string {
    const { namespace, idTemplate } = testConfig.things;
    return `${namespace}:${idTemplate}${index + testConfig.things.startIndex}`;
}

/**
 * Get a random thing ID from the configured range.
 * @returns Random thing ID
 */
export function getRandomThingId(testConfig: TestConfigData): string {
    const index = Math.floor(Math.random() * testConfig.things.count);
    return getThingId(testConfig, index);
}

/**
 * Get the full device ID template with namespace.
 * @returns Device ID template (e.g., "org.eclipse.ditto:test-thing-")
 */
export function getDeviceIdTemplate(testConfig: TestConfigData): string {
    const { namespace, idTemplate } = testConfig.things;
    return `${namespace}:${idTemplate}`;
}

/**
 * Get Kafka bootstrap servers as an array.
 * @returns Array of bootstrap server addresses
 */
export function getKafkaBootstrapServers(testConfig: TestConfigData): string[] {
    return testConfig.connections.kafka.bootstrapServers
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0);
}

/**
 * Check if a scenario is enabled.
 * A scenario is enabled if enabled=true and has at least one channel with weight > 0.
 * @param scenarioKey - Scenario key (e.g., 'readThings')
 * @returns true if scenario is enabled
 */
export function isScenarioEnabled(testConfig: TestConfigData, scenarioKey: string): boolean {
    const scenario = testConfig.scenarios.find(scenario => scenario.name === scenarioKey);
    if (!scenario || !scenario.enabled) {
        return false;
    }
    const channels = scenario.channels || {};
    return channels.some(channel => channel.weight > 0);
}

/**
 * Get the list of enabled scenarios.
 * A scenario is enabled if it has at least one channel with weight > 0.
 * @returns Array of enabled ScenarioConfig
 */
export function getConfigEnabledScenarios(testConfig: TestConfigData): ScenarioConfig[] {
    return testConfig.scenarios.filter(scenario =>
        scenario.enabled && scenario.channels.some(channel => channel.weight > 0)
    );
}

/**
 * Get enabled channels for a specific scenario.
 * @param scenarioName - Scenario name (e.g., 'readThings')
 * @returns Array of ScenarioChannelWeights with weight > 0
 */
export function getEnabledChannels(testConfig: TestConfigData, scenarioName: ScenarioName): ScenarioChannelWeights[] {
    const scenario = testConfig.scenarios.find(s => s.name === scenarioName);
    if (!scenario || !scenario.enabled) {
        return [];
    }
    return scenario.channels.filter(ch => ch.weight > 0);
}

/**
 * Get timeout for a specific scenario.
 * @param scenarioName - Scenario name (e.g., 'modifyThings')
 * @returns Timeout in seconds
 */
export function getScenarioTimeout(testConfig: TestConfigData, scenarioName: ScenarioName): number {
    const scenario = testConfig.scenarios.find(s => s.name === scenarioName);
    return scenario?.timeout ?? DEFAULT_SCENARIO_TIMEOUT;
}

/**
 * Get default configuration values.
 * Used when YAML config is missing fields.
 */
export function getDefaultTestConfig(): TestConfigData {
    return {
        ditto: getDefaultDittoConfig(),
        things: getDefaultThingsConfig(),
        connections: getDefaultConnectionsConfig(),
        artillery: getDefaultArtilleryConfig(),
        scenarios: getDefaultScenariosConfig(),
        warmup: getDefaultWarmupConfig(),
        cleanupOnly: false,
    };
}
