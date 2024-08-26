"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const common_objects = {
    console: { [READ]: { supported: ["8.0.0"] } },
    close: { [READ]: { supported: ["9.0.0"] } },
    open: { [READ]: { supported: ["8.0.0"] } },
    url: { [READ]: { supported: ["8.0.0"] } },
    waitForDebugger: { [READ]: { supported: ["12.7.0"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
const promises_api = {
    Session: { [READ]: { supported: ["19.0.0"] } },
    ...common_objects,
}

/** @type {import('../types.js').SupportVersionTraceMap} */
const callback_api = {
    Session: { [READ]: { supported: ["8.0.0"] } },
    ...common_objects,
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    inspector: {
        [READ]: {
            experimental: ["8.0.0"],
            supported: ["14.0.0"],
        },
        ...callback_api,
    },
    "node:inspector": {
        [READ]: { supported: ["14.13.1", "12.20.0"] },
        ...callback_api,
    },

    "inspector/promises": {
        [READ]: { experimental: ["19.0.0"] },
        ...promises_api,
    },
    "node:inspector/promises": {
        [READ]: { experimental: ["19.0.0"] },
        ...promises_api,
    },
}
