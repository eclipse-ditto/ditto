import { READ } from "@eslint-community/eslint-utils"

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
const sqlite = {
    constants: {
        [READ]: { supported: ["22.13.0", "23.5.0"] },
        SQLITE_CHANGESET_OMIT: { [READ]: { supported: ["22.13.0", "23.5.0"] } },
        SQLITE_CHANGESET_REPLACE: {
            [READ]: { supported: ["22.13.0", "23.5.0"] },
        },
        SQLITE_CHANGESET_ABORT: {
            [READ]: { supported: ["22.13.0", "23.5.0"] },
        },
    },
    backup: { [READ]: { supported: ["22.16.0", "23.8.0"] } },
    DatabaseSync: { [READ]: { supported: ["22.5.0"] } },
    Session: { [READ]: { supported: ["22.12.0"] } },
    StatementSync: { [READ]: { supported: ["22.5.0"] } },
    SQLITE_CHANGESET_OMIT: {
        [READ]: {
            experimental: ["22.12.0"],
            deprecated: ["22.13.0"],
        },
    },
    SQLITE_CHANGESET_REPLACE: {
        [READ]: {
            experimental: ["22.12.0"],
            deprecated: ["22.13.0"],
        },
    },
    SQLITE_CHANGESET_ABORT: {
        [READ]: {
            experimental: ["22.12.0"],
            deprecated: ["22.13.0"],
        },
    },
}

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
export default {
    "node:sqlite": {
        [READ]: { experimental: ["22.5.0"] },
        ...sqlite,
    },
}
