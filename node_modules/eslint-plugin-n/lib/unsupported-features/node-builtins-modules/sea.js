import { READ } from "@eslint-community/eslint-utils"

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
const sea = {
    isSea: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
    getAsset: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
    getAssetAsBlob: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
    getAssetKeys: { [READ]: { supported: ["22.20.0", "24.8.0"] } },
    getRawAsset: { [READ]: { supported: ["21.7.0", "20.12.0"] } },
    sea: {},
}

sea.sea = sea

/**
 * @satisfies {import('../types.js').SupportVersionTraceMap}
 */
export default {
    "node:sea": {
        [READ]: { experimental: ["21.7.0", "20.12.0"] },
        ...sea,
    },
}
