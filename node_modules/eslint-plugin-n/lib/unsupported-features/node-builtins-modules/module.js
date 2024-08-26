"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const Module = {
    builtinModules: { [READ]: { supported: ["9.3.0", "8.10.0", "6.13.0"] } },
    createRequire: { [READ]: { supported: ["12.2.0"] } },
    createRequireFromPath: {
        [READ]: {
            supported: ["10.12.0"],
            deprecated: ["12.2.0"],
        },
    },
    isBuiltin: { [READ]: { supported: ["18.6.0", "16.17.0"] } },
    register: { [READ]: { experimental: ["20.6.0"] } },
    syncBuiltinESMExports: { [READ]: { supported: ["12.12.0"] } },
    findSourceMap: { [READ]: { supported: ["13.7.0", "12.17.0"] } },
    SourceMap: { [READ]: { supported: ["13.7.0", "12.17.0"] } },
}

Module.Module = Module

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    module: {
        [READ]: { supported: ["0.3.7"] },
        ...Module,
    },
    "node:module": {
        [READ]: { supported: ["14.13.1", "12.20.0"] },
        ...Module,
    },
}
