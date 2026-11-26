export type Configs = {
    "recommended-module": ESLint.ConfigData;
    "recommended-script": ESLint.ConfigData;
    "recommended": ESLint.ConfigData;
    "flat/recommended-module": Linter.Config;
    "flat/recommended-script": Linter.Config;
    "flat/recommended": Linter.Config;
    "flat/mixed-esm-and-cjs": Linter.Config[];
    "flat/all": Linter.Config;
};
/** @type {ESLint.Plugin & { configs: Configs }} */
declare const n: ESLint.Plugin & {
    configs: Configs;
};
import type { ESLint } from 'eslint';
import type { Linter } from 'eslint';
export { n as default, n as "module.exports" };
