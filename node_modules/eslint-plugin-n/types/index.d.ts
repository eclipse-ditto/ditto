export = plugin;
/**
 * @typedef {{
     'recommended-module': import('eslint').ESLint.ConfigData;
     'recommended-script': import('eslint').ESLint.ConfigData;
     'recommended': import('eslint').ESLint.ConfigData;
     'flat/recommended-module': import('eslint').Linter.FlatConfig;
     'flat/recommended-script': import('eslint').Linter.FlatConfig;
     'flat/recommended': import('eslint').Linter.FlatConfig;
     'flat/mixed-esm-and-cjs': import('eslint').Linter.FlatConfig[];
 }} Configs
 */
/** @type {import('eslint').ESLint.Plugin & { configs: Configs }} */

// @ts-ignore
declare const plugin: import('eslint').ESLint.Plugin & {
    configs: Configs;
};
type Configs = {
    
// @ts-ignore
'recommended-module': import('eslint').ESLint.ConfigData;
    
// @ts-ignore
'recommended-script': import('eslint').ESLint.ConfigData;
    
// @ts-ignore
'recommended': import('eslint').ESLint.ConfigData;
    
// @ts-ignore
'flat/recommended-module': import('eslint').Linter.FlatConfig;
    
// @ts-ignore
'flat/recommended-script': import('eslint').Linter.FlatConfig;
    
// @ts-ignore
'flat/recommended': import('eslint').Linter.FlatConfig;
    
// @ts-ignore
'flat/mixed-esm-and-cjs': import('eslint').Linter.FlatConfig[];
};
