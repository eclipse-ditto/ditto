"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const perf_hooks = {
    performance: { [READ]: { supported: ["8.5.0"] } },
    createHistogram: { [READ]: { supported: ["15.9.0", "14.18.0"] } },
    monitorEventLoopDelay: { [READ]: { supported: ["11.10.0"] } },
    PerformanceEntry: { [READ]: { supported: ["8.5.0"] } },
    PerformanceMark: { [READ]: { supported: ["18.2.0", "16.17.0"] } },
    PerformanceMeasure: { [READ]: { supported: ["18.2.0", "16.17.0"] } },
    PerformanceNodeEntry: { [READ]: { supported: ["19.0.0"] } },
    PerformanceNodeTiming: { [READ]: { supported: ["8.5.0"] } },
    PerformanceResourceTiming: { [READ]: { supported: ["18.2.0", "16.17.0"] } },
    PerformanceObserver: { [READ]: { supported: ["8.5.0"] } },
    PerformanceObserverEntryList: { [READ]: { supported: ["8.5.0"] } },
    Histogram: { [READ]: { supported: ["11.10.0"] } },
    IntervalHistogram: { [READ]: { supported: ["8.5.0"] } },
    RecordableHistogram: { [READ]: { supported: ["15.9.0", "14.18.0"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    perf_hooks: {
        [READ]: { supported: ["8.5.0"] },
        ...perf_hooks,
    },
    "node:perf_hooks": {
        [READ]: { supported: ["14.13.1", "12.20.0"] },
        ...perf_hooks,
    },
}
