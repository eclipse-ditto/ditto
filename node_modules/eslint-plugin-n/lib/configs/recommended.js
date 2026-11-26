import { getPackageJson } from "../util/get-package-json.js"
import * as moduleConfig from "./recommended-module.js"
import * as scriptConfig from "./recommended-script.js"

const packageJson = getPackageJson()

const isModule =
    packageJson != null &&
    typeof packageJson === "object" &&
    "type" in packageJson &&
    packageJson.type === "module"
const recommendedConfig = isModule ? moduleConfig : scriptConfig

/**
 * https://eslint.org/docs/latest/use/configure/configuration-files
 * @type {import('eslint').ESLint.ConfigData}
 */
export const eslintrc = {
    ...recommendedConfig.eslintrc,
    overrides: [
        { files: ["*.cjs", ".*.cjs"], ...scriptConfig.eslintrc },
        { files: ["*.mjs", ".*.mjs"], ...moduleConfig.eslintrc },
    ],
}

export const flat = recommendedConfig.flat
