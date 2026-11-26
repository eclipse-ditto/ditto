import globals from "globals"
import { recommendeRulesConfig } from "./_commons.js"

/**
 * https://eslint.org/docs/latest/use/configure/configuration-files
 * @type {import('eslint').ESLint.ConfigData}
 */
export const eslintrc = {
    env: {
        node: true,
    },
    globals: {
        ...globals.es2021,
        __dirname: "off",
        __filename: "off",
        exports: "off",
        module: "off",
        require: "off",
    },
    parserOptions: {
        ecmaFeatures: { globalReturn: false },
        ecmaVersion: 2021,
        sourceType: "module",
    },
    rules: {
        ...recommendeRulesConfig,
        "n/no-unsupported-features/es-syntax": [
            "error",
            { ignores: ["modules"] },
        ],
    },
}

/**
 * https://eslint.org/docs/latest/use/configure/configuration-files-new
 * @type {import('eslint').Linter.FlatConfig}
 */
export const flat = {
    name: "node/flat/recommended-module",
    languageOptions: {
        sourceType: "module",
        globals: {
            ...globals.node,
            ...eslintrc.globals,
        },
    },
    rules:
        /** @type {import('eslint').Linter.RulesRecord} */
        (eslintrc.rules),
}
