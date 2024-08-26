"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const test = {
    run: { [READ]: { supported: ["18.9.0", "16.19.0"] } },
    skip: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
    todo: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
    only: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
    describe: {
        [READ]: { supported: ["18.7.0", "16.17.0"] },
        skip: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
        todo: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
        only: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
    },
    it: {
        [READ]: { supported: ["18.6.0", "16.17.0"] },
        skip: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
        todo: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
        only: { [READ]: { supported: ["20.2.0", "18.17.0"] } },
    },
    suite: {
        [READ]: { supported: ["22.0.0", "20.13.0"] },
        skip: { [READ]: { supported: ["22.0.0", "20.13.0"] } },
        todo: { [READ]: { supported: ["22.0.0", "20.13.0"] } },
        only: { [READ]: { supported: ["22.0.0", "20.13.0"] } },
    },
    before: { [READ]: { supported: ["18.8.0", "16.18.0"] } },
    after: { [READ]: { supported: ["18.8.0", "16.18.0"] } },
    beforeEach: { [READ]: { supported: ["18.8.0", "16.18.0"] } },
    afterEach: { [READ]: { supported: ["18.8.0", "16.18.0"] } },
    snapshot: {
        [READ]: { experimental: ["22.3.0"] },
        setDefaultSnapshotSerializers: { [READ]: { experimental: ["22.3.0"] } },
        setResolveSnapshotPath: { [READ]: { experimental: ["22.3.0"] } },
    },
    MockFunctionContext: { [READ]: { supported: ["19.1.0", "18.13.0"] } },
    MockModuleContext: { [READ]: { experimental: ["22.3.0"] } },
    MockTracker: { [READ]: { supported: ["19.1.0", "18.13.0"] } },
    MockTimers: { [READ]: { experimental: ["20.4.0"] } },
    TestsStream: { [READ]: { supported: ["18.9.0", "16.19.0"] } },
    TestContext: { [READ]: { supported: ["18.0.0", "16.17.0"] } },
    SuiteContext: { [READ]: { supported: ["18.7.0", "16.17.0"] } },
}

test.test = test

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    "node:test": {
        [READ]: {
            experimental: ["18.7.0", "16.17.0"],
            supported: ["20.0.0"],
        },
        ...test,
    },
}
