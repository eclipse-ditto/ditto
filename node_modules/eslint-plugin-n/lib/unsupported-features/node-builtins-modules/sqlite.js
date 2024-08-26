"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const sqlite = {
    DatabaseSync: { [READ]: { supported: ["22.5.0"] } },
    StatementSync: { [READ]: { supported: ["22.5.0"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    "node:sqlite": {
        [READ]: { experimental: ["22.5.0"] },
        ...sqlite,
    },
}
