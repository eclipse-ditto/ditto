"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const test = {
    isSea: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
    getAsset: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
    getAssetAsBlob: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
    getRawAsset: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
}

test.test = test

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    "node:sea": {
        [READ]: { experimental: ["21.7.0", "20.12.0"] },
        ...test,
    },
}
