"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const string_decoder = {
    StringDecoder: { [READ]: { supported: ["0.1.99"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    string_decoder: {
        [READ]: { supported: ["0.1.99"] },
        ...string_decoder,
    },
    "node:string_decoder": {
        [READ]: { supported: ["14.13.1", "12.20.0"] },
        ...string_decoder,
    },
}
