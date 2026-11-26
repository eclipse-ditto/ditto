import { READ } from "@eslint-community/eslint-utils"

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
const wasi = {
    WASI: { [READ]: { supported: ["13.3.0", "12.16.0"] } },
}

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
export default {
    wasi: wasi,
    "node:wasi": { ...wasi, [READ]: { supported: ["14.13.1", "12.20.0"] } },
}
