"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const http = {
    globalAgent: { [READ]: { supported: ["0.5.9"] } },
    createServer: { [READ]: { supported: ["0.3.4"] } },
    get: { [READ]: { supported: ["0.3.6"] } },
    request: { [READ]: { supported: ["0.3.6"] } },
    Agent: { [READ]: { supported: ["0.4.5"] } },
    Server: { [READ]: { supported: ["0.3.4"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    http: {
        [READ]: { supported: ["0.3.4"] },
        ...http,
    },
    "node:http": {
        [READ]: { supported: ["14.13.1", "12.20.0"] },
        ...http,
    },
}
