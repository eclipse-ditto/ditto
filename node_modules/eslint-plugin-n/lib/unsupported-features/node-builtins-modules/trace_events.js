import { READ } from "@eslint-community/eslint-utils"

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
const trace_events = {
    createTracing: { [READ]: { supported: ["10.0.0"] } },
    getEnabledCategories: { [READ]: { supported: ["10.0.0"] } },
}

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
export default {
    trace_events: {
        [READ]: { experimental: ["10.0.0"] },
        ...trace_events,
    },
    "node:trace_events": {
        [READ]: { experimental: ["14.13.1", "12.20.0"] },
        ...trace_events,
    },
}
