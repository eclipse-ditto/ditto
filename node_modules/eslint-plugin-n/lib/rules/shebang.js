/**
 * @fileoverview the rule has been renamed to `hashbang`. Please use `hashbang` instead.
 * @deprecated
 * @author 唯然<weiran.zsd@outlook.com>
 */

import hashbang from "./hashbang.js"

const hashbangDocs = hashbang.meta?.docs
const hashbangUrl =
    hashbangDocs?.url ??
    "https://github.com/eslint-community/eslint-plugin-n/blob/HEAD/docs/rules/hashbang.md"

export default {
    meta: {
        ...hashbang.meta,
        deprecated: {
            deprecatedSince: "17.0.0",
            message:
                "This rule was deprecated in eslint-plugin-n v17.0.0. Please use 'n/hashbang'",
            url: "https://github.com/eslint-community/eslint-plugin-n/issues/196",
            replacedBy: [
                {
                    rule: {
                        name: "n/hashbang",
                        url: hashbangUrl,
                    },
                },
            ],
        },
        docs: { ...hashbangDocs, recommended: false, url: hashbangUrl },
    },
    create: hashbang.create,
}
