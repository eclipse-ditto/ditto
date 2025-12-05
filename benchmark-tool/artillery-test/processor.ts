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
import {
    getConfig,
    ALL_CHANNELS,
    CHANNEL_HTTP,
    WARMUP
} from './common';
import { debugLog } from './utils';
import { TestSetup } from './test-setup';
import { warmupScenario } from './scenarios/warmup-scenario';
import { readThingsScenario } from './scenarios/read-things-scenario';
import { searchThingsScenario } from './scenarios/search-things-scenario';
import { modifyThingsScenario } from './scenarios/modify-things-scenario';
import { deviceLiveMessagesScenario } from './scenarios/device-live-messages-scenario';
import { isServiceAvailable } from './cleanup-util';
import { setInterrupted } from './kafka-util';
import { Scenario } from './interfaces';
import { getConfigEnabledScenarios } from './config';

// ======================
// GLOBAL STATE (per worker thread)
// ======================

let testSetup: TestSetup | null = null;
let cleanupInProgress = false;
let setupFailed = false;

// ======================
// ABORT FILE HANDLING
// This is needed, because artillery manages the
// threads, and it is not possible to pass data between them
// in a robust manner, to abort the
// test and exit gracefully. What happens, is sometimes
// artillery fails to immediately stop its workers
// which leads to scenarios continuing to execute for
// unknown amount of time
// ======================

const ABORT_FILE_DIR = '/tmp'
const ABORT_FILE_NAME = '.artillery_abort_signal';
const ABORT_FILE = `${ABORT_FILE_DIR}/${ABORT_FILE_NAME}`;
let testAborted = false;
let abortWatcher: fs.FSWatcher | null = null;

function setupAbortWatcher() {
    if (abortWatcher) return;

    abortWatcher = fs.watch(ABORT_FILE_DIR, (eventType, filename) => {
        if (filename === ABORT_FILE_NAME && fs.existsSync(ABORT_FILE) && !testAborted) {
            debugLog('[Abort] Abort file detected, stopping scenarios');
            testAborted = true;
        }
    });
    abortWatcher.unref();
}

function cleanupAbortFile() {
    try {
        if (fs.existsSync(ABORT_FILE)) {
            fs.unlinkSync(ABORT_FILE);
            debugLog('[Abort] Cleaned up abort file');
        }
    } catch (e) { /* ignore */ }
}

function signalAbort() {
    if (!testAborted) {
        fs.writeFileSync(ABORT_FILE, '1');
        testAborted = true;
        debugLog('[Abort] Abort signal sent');
    } else {
        debugLog('[Abort] Skip Abort signal, already sent');
    }
}

function closeAbortWatcher() {
    if (abortWatcher) {
        abortWatcher.close();
        abortWatcher = null;
    }
}

// Setup watcher when module loads (per worker thread)
setupAbortWatcher();

// ======================
// LIFECYCLE HOOKS
// ======================

/**
 * Called once before any VUs start
 */
export async function beforeTest(context: any, events: any): Promise<void> {
    console.log('=== SETUP PHASE ===');

    // Clean up any leftover abort file from previous run
    cleanupAbortFile();

    const config = getConfig();

    // Check if service is available before starting test
    debugLog('[Setup] Checking if Ditto service is available...');
    const serviceAvailable = await isServiceAvailable();

    if (!serviceAvailable) {
        console.error('=====================================');
        console.error('ERROR: Ditto service is not available');
        console.error('=====================================');
        console.error(`Service URL: ${config.ditto.protocol.http}://${config.ditto.baseUri}`);
        console.error('');
        console.error('Test aborted.');
        console.error('=====================================');
        throw new Error('Ditto service is not available');
    }

    debugLog('[Setup] Ditto service is available');

    const enabledScenarios = getConfigEnabledScenarios(config);

    let shouldExitAfterSetup = false;

    if (config.cleanupOnly) {
        console.log('[Setup] Cleanup-only mode, no scenarios will run.');
        try {
            testSetup = new TestSetup(true);
            await testSetup.cleanup();
        } catch (error: any) {
            console.error('[Setup] Cleanup failed:', error);
        }
        process.exit(0);
    } else if (enabledScenarios.length === 0) {
        if (config.things.createBeforeTest) {
            shouldExitAfterSetup = true;
        } else {
            console.log('[Setup] No scenarios configured to run.');
            process.exit(0);
        }
    }

    testSetup = new TestSetup();
    try {
        testSetup.cleanupOnly = true;
        await testSetup.setupConnections();
        await testSetup.createThings();

        if (shouldExitAfterSetup) {
            process.exit(0);
        }

        testSetup.cleanupOnly = false;

        debugLog('=== SETUP COMPLETE ===');
    } catch (error) {
        setupFailed = true;
        console.error('Setup failed:', error);

        // Cleanup might be already in progress, if SIGINT/SIGTERM signal is sent
        if (!cleanupInProgress) {
            await performCleanup();
            process.exit(0);
        }
    }
}


/**
 * Warmup scenario wrapper - controlled by config warmup.enabled (always uses HTTP)
 * ONLY executed in 'before' phase, right after setup
 */
export async function runWarmupIfEnabled(context: any, events: any): Promise<void> {
    if (setupFailed || !getConfig().warmup.enabled) {
        return;
    }

    const vuId = context._uid || context.vars?.$uuid || 'unknown';
    debugLog(`[Scenario: ${WARMUP}] [VU: ${vuId}] Starting`);

    context.vars = context.vars || {};
    context.vars.channel = CHANNEL_HTTP;

    await warmupScenario.execute(context, events);

    debugLog(`[Scenario: ${WARMUP}] [VU: ${vuId}] Completed`);
}

/**
 * Called once after scenario execution, or in case of interruption
 */
export async function afterTest(context: any, events: any): Promise<void> {
    await performCleanup();
}

async function performCleanup() {
    if (cleanupInProgress) {
        console.log('[Cleanup] Already in progress, skipping...');
        return;
    }

    console.log('\n=== CLEANUP PHASE ===');
    cleanupInProgress = true;

    signalAbort();
    setInterrupted();

    // Handle SIGINT/SIGTERM during cleanup - print cleanup data
    const cleanupHandler = (signal: NodeJS.Signals) => {
        console.log(`\n[${signal}] Cleanup interrupted`);
        testSetup?.logManualCleanupInstructions();
        process.exit(0);
    };

    process.on('SIGINT', cleanupHandler);
    process.on('SIGTERM', cleanupHandler);

    try {
        if (testSetup) {
            await testSetup.cleanup();
        }
    } catch (error) {
        console.error('[Cleanup] Error during cleanup:', error);
    } finally {
        closeAbortWatcher();
        cleanupAbortFile();
    }
}

// ======================
// SCENARIO WRAPPER FUNCTIONS
// ======================

/**
 * Extract channel from scenario name (e.g., 'READ_THINGS_HTTP' -> 'HTTP')
 */
function extractChannelFromScenarioName(scenarioName: string): string {
    for (const channel of ALL_CHANNELS) {
        if (scenarioName.endsWith(`_${channel}`)) {
            return channel;
        }
    }
    console.warn(`[Processor] Could not extract channel from scenario name: ${scenarioName}, defaulting to '${CHANNEL_HTTP}'`);
    return CHANNEL_HTTP;
}

/**
 * Prepares context for scenario execution. Returns false if scenario should be skipped.
 */
function prepareScenarioContext(context: any): boolean {
    if (testAborted) return false;

    const scenarioName = context.scenario?.name || 'Unknown';
    const channel = extractChannelFromScenarioName(scenarioName);

    context.vars = context.vars || {};
    context.vars.channel = channel;
    context.vars.scenarioName = scenarioName;
    context.vars.vuId = context._uid || context.vars?.$uuid || 'unknown';

    debugLog(`[Scenario: ${scenarioName}] [VU: ${context.vars.vuId}] Starting`);
    return true;
}

function logScenarioCompleted(context: any): void {
    debugLog(`[Scenario: ${context.vars.scenarioName}] [VU: ${context.vars.vuId}] Completed`);
}

export async function runReadThings(context: any, events: any): Promise<void> {
    await runScenario(context, events, readThingsScenario);
}

export async function runSearchThings(context: any, events: any): Promise<void> {
    await runScenario(context, events, searchThingsScenario)
}

export async function runModifyThings(context: any, events: any): Promise<void> {
    await runScenario(context, events, modifyThingsScenario);
}

export async function runDeviceLiveMessages(context: any, events: any): Promise<void> {
    await runScenario(context, events, deviceLiveMessagesScenario);

}

async function runScenario(context: any, events: any, scenario: Scenario): Promise<void> {
    if (!prepareScenarioContext(context)) return;
    await scenario.execute(context, events);
    logScenarioCompleted(context);
}
