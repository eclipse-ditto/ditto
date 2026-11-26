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
        __dirname: "readonly",
        __filename: "readonly",
        exports: "writable",
        module: "readonly",
        require: "readonly",
    },
    parserOptions: {
        ecmaFeatures: { globalReturn: true },
        ecmaVersion: 2021,
        sourceType: "script",
    },
    rules: {
        ...recommendeRulesConfig,
        "n/no-unsupported-features/es-syntax": ["error", { ignores: [] }],
    },
}

/**
 * https://eslint.org/docs/latest/use/configure/configuration-files-new
 * @type {import('eslint').Linter.FlatConfig}
 */
export const flat = {
    name: "node/flat/recommended-script",
    languageOptions: {
        sourceType: "commonjs",
        globals: {
            ...globals.node,
            ...eslintrc.globals,
        },
    },
    rules:
        /** @type {import('eslint').Linter.RulesRecord} */
        (eslintrc.rules),
}
