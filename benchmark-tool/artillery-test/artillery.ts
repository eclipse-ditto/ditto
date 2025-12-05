/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
    getConfig,
    READ_THINGS,
    SEARCH_THINGS,
    MODIFY_THINGS,
    DEVICE_LIVE_MESSAGES,
    SCENARIO_SUPPORTED_CHANNELS,
    ScenarioName,
    WARMUP
} from './common';
import { getConfigEnabledScenarios } from './config';
import { beforeTest, afterTest, runWarmupIfEnabled } from './processor';
import { debugLog } from './utils';

// Dynamically determine processor path
function getProcessorPath(): string {
    // This is due to artillery processor config accepts only filename to read the processor from, 
    // which can be .ts if built locally and .js if built in docker image
    return process.env.NODE_ENV === 'docker' ? './processor.js' : './processor.ts';
}

const SCENARIO_FUNCTION_NAMES: Record<ScenarioName, string> = {
    [WARMUP]: 'runWarmupIfEnabled',
    [READ_THINGS]: 'runReadThings',
    [SEARCH_THINGS]: 'runSearchThings',
    [MODIFY_THINGS]: 'runModifyThings',
    [DEVICE_LIVE_MESSAGES]: 'runDeviceLiveMessages'
};

export const config = {
    target: getConfig().ditto.baseUri,
    processor: getProcessorPath(),
    phases: [
        {   
            name: 'Ditto Load test',
            duration: getConfig().artillery.duration,
            arrivalRate: getConfig().artillery.arrivalRate
        }
    ]
};

export const before = {
    flow: [
        { function: beforeTest.name },
        { function: runWarmupIfEnabled.name }
    ]
};


export const after = {
    flow: [
        { function: afterTest.name }
    ]
};

export const scenarios = getEnabledScenarios();

function getEnabledScenarios(): any[] {
    debugLog(`[Config] parsed config: ${JSON.stringify(getConfig(), null, 2)}`);
    const generatedScenarios: any[] = [];
    const enabledScenarios = getConfigEnabledScenarios(getConfig());

    enabledScenarios.forEach(scenario => {
        const functionName = SCENARIO_FUNCTION_NAMES[scenario.name];

        if (!functionName) {
            console.warn(`[Artillery Config] Unknown scenario: ${scenario.name}`);
            return;
        }

        const supportedChannels = SCENARIO_SUPPORTED_CHANNELS[scenario.name];

        scenario.channels.forEach(channelConfig => {
            if (channelConfig.weight === 0) {
                console.log(`[Artillery Config] Skipping ${scenario.name}_${channelConfig.name} - disabled by weight`);
                return;
            }

            if (!supportedChannels.includes(channelConfig.name)) {
                console.warn(`[Artillery Config] Skipping ${scenario.name}_${channelConfig.name} - scenario doesn't support this channel. Supported: ${supportedChannels.join(', ')}`);
                return;
            }

            generatedScenarios.push({
                name: `${scenario.name}_${channelConfig.name}`,
                weight: channelConfig.weight,
                flow: [{ function: functionName }]
            });
        });
    });

    console.log(`[Artillery Config] Generated scenarios: ${generatedScenarios.map(s => s.name).join(', ') || []}`);

    return generatedScenarios;
}
