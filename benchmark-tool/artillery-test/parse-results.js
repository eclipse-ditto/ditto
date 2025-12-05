#!/usr/bin/env node
/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

const fs = require('fs');
const path = require('path');

// Operations (for backward compatibility)
const OPERATIONS = [
    'getThing',
    'getThingsBatch',
    'searchThings',
    'modifyThing',
    'sendLiveMessage'
];

// Scenarios and channels
const SCENARIOS = ['readThings', 'searchThings', 'modifyThings', 'deviceLiveMessages'];
const CHANNELS = ['http', 'websocketTwin', 'websocketLive', 'kafka'];

function parseResults(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    const report = JSON.parse(content);

    console.log('='.repeat(80));
    console.log('ARTILLERY TEST RESULTS');
    console.log('='.repeat(80));
    console.log();

    // Find aggregate data - Artillery stores it differently depending on version
    const aggregate = report.aggregate || {};
    const counters = aggregate.counters || {};
    const summaries = aggregate.summaries || {};
    const histograms = aggregate.histograms || {};

    // Test summary
    console.log('TEST SUMMARY');
    console.log('-'.repeat(40));

    // Calculate duration from timestamps
    const firstCounterAt = aggregate.firstCounterAt || aggregate.firstMetricAt;
    const lastCounterAt = aggregate.lastCounterAt || aggregate.lastMetricAt;
    if (firstCounterAt && lastCounterAt) {
        console.log(`Duration: ${formatDuration(lastCounterAt - firstCounterAt)}`);
    }

    // Scenarios created/completed - check various locations
    const scenariosCreated = counters['vusers.created'] ||
                            counters['vusers.created_by_name'] ||
                            aggregate.scenariosCreated ||
                            sumCountersByPrefix(counters, 'vusers.created_by_name.');
    const scenariosCompleted = counters['vusers.completed'] ||
                              aggregate.scenariosCompleted ||
                              sumCountersByPrefix(counters, 'vusers.completed');
    const scenariosFailed = counters['vusers.failed'] || aggregate.scenariosFailed || 0;

    console.log(`Scenarios Created:   ${scenariosCreated || 'N/A'}`);
    console.log(`Scenarios Completed: ${scenariosCompleted || 'N/A'}`);
    if (scenariosFailed) {
        console.log(`Scenarios Failed:    ${scenariosFailed}`);
    }
    console.log();

    // Results per scenario and channel
    // Scenario names in Artillery are like: readThings_http, modifyThings_kafka, etc.
    console.log('RESULTS BY SCENARIO & CHANNEL');
    console.log('-'.repeat(40));

    let hasScenarioMetrics = false;
    for (const scenario of SCENARIOS) {
        for (const channel of CHANNELS) {
            // Match the Artillery scenario naming: scenarioName_channel
            const prefix = `${scenario}_${channel}`;
            const metrics = extractMetrics(counters, summaries, histograms, prefix);

            if (metrics.requests > 0) {
                hasScenarioMetrics = true;
                printMetrics(`${scenario} [${channel}]`, metrics);
            }
        }
    }

    // Also check for any custom metrics that might have different naming patterns
    if (!hasScenarioMetrics) {
        // Try to find any scenario-like metrics in counters
        const scenarioMetricKeys = Object.keys(counters).filter(key =>
            key.endsWith('.requests') &&
            !OPERATIONS.some(op => key.startsWith(op + '.'))
        );

        if (scenarioMetricKeys.length > 0) {
            for (const key of scenarioMetricKeys) {
                const prefix = key.replace('.requests', '');
                const metrics = extractMetrics(counters, summaries, histograms, prefix);
                if (metrics.requests > 0) {
                    hasScenarioMetrics = true;
                    printMetrics(prefix, metrics);
                }
            }
        }
    }

    if (!hasScenarioMetrics) {
        console.log('  No scenario-level metrics found.');
    }
    console.log();

    // Results by operation (aggregate)
    console.log('RESULTS BY OPERATION (AGGREGATE)');
    console.log('-'.repeat(40));

    let hasOperationMetrics = false;
    for (const op of OPERATIONS) {
        const metrics = extractMetrics(counters, summaries, histograms, op);

        if (metrics.requests > 0) {
            hasOperationMetrics = true;
            printMetrics(op, metrics);
        }
    }

    if (!hasOperationMetrics) {
        console.log('  No operation-level metrics found.');
    }
    console.log();

    // HTTP response codes
    const codes = aggregate.codes || {};
    if (Object.keys(codes).length > 0) {
        console.log('HTTP RESPONSE CODES');
        console.log('-'.repeat(40));
        for (const [code, count] of Object.entries(codes)) {
            console.log(`  ${code}: ${count}`);
        }
        console.log();
    }

    // Errors
    const errors = aggregate.errors || {};
    if (Object.keys(errors).length > 0) {
        console.log('ERRORS');
        console.log('-'.repeat(40));
        for (const [error, count] of Object.entries(errors)) {
            console.log(`  ${error}: ${count}`);
        }
        console.log();
    }

    console.log('='.repeat(80));
}

function extractMetrics(counters, summaries, histograms, prefix) {
    const requests = counters[`${prefix}.requests`] || 0;
    const success = counters[`${prefix}.success`] || 0;
    const errors = counters[`${prefix}.errors`] || 0;
    const responseTime = summaries[`${prefix}.response_time`] ||
                        histograms[`${prefix}.response_time`] ||
                        null;

    return { requests, success, errors, responseTime };
}

function printMetrics(label, metrics) {
    const { requests, success, errors, responseTime } = metrics;
    const successRate = requests > 0 ? ((success / requests) * 100).toFixed(2) : 'N/A';

    console.log();
    console.log(`[${label}]`);
    console.log(`  Requests:     ${requests}`);
    console.log(`  Success:      ${success}`);
    console.log(`  Errors:       ${errors}`);
    console.log(`  Success Rate: ${successRate}%`);

    if (responseTime) {
        console.log(`  Response Time (ms):`);
        console.log(`    Min:    ${formatNumber(responseTime.min)}`);
        console.log(`    Max:    ${formatNumber(responseTime.max)}`);
        console.log(`    Mean:   ${formatNumber(responseTime.mean)}`);
        console.log(`    Median: ${formatNumber(responseTime.median || responseTime.p50)}`);
        console.log(`    p95:    ${formatNumber(responseTime.p95)}`);
        console.log(`    p99:    ${formatNumber(responseTime.p99)}`);
    }
}

function sumCountersByPrefix(counters, prefix) {
    let sum = 0;
    for (const [key, value] of Object.entries(counters)) {
        if (key.startsWith(prefix)) {
            sum += value;
        }
    }
    return sum || null;
}

function formatNumber(value) {
    if (value === undefined || value === null) return 'N/A';
    return typeof value === 'number' ? value.toFixed(2) : value;
}

function formatDuration(ms) {
    if (!ms) return 'N/A';
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes > 0) {
        return `${minutes}m ${remainingSeconds}s`;
    }
    return `${seconds}s`;
}

// Main
const args = process.argv.slice(2);
if (args.length === 0) {
    console.error('Usage: node parse-results.js <report.json>');
    process.exit(1);
}

const filePath = path.resolve(args[0]);
if (!fs.existsSync(filePath)) {
    console.error(`File not found: ${filePath}`);
    process.exit(1);
}

try {
    parseResults(filePath);
} catch (error) {
    console.error('Failed to parse results:', error.message);
    process.exit(1);
}
