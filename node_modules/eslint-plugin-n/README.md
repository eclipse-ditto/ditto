# eslint-plugin-n

> forked from [eslint-plugin-node](https://www.npmjs.com/package/eslint-plugin-node) v11.1.0. as the original repository seems [no longer maintained](https://github.com/mysticatea/eslint-plugin-node/issues/300).

[![npm version](https://img.shields.io/npm/v/eslint-plugin-n.svg)](https://www.npmjs.com/package/eslint-plugin-n)
[![Downloads](https://img.shields.io/npm/dm/eslint-plugin-n.svg)](https://www.npmjs.com/package/eslint-plugin-n)
[![Build Status](https://github.com/eslint-community/eslint-plugin-n/workflows/CI/badge.svg)](https://github.com/eslint-community/eslint-plugin-n/actions)

Additional ESLint rules for Node.js

## 🎨 Playground

[online-playground](https://eslint-online-playground.netlify.app/#eNptjzFuwzAMRa8icGqB2NndtbcoOxgybaiVSUGSgxSG717KUoAMWSiReJ//c4cU7ZXu4xo89T8JBrDCSbTxsryFKJZS6olv7x/IcAFK3nHuFZrdUgVuDRKzYTNHWQ02pAt+Wxx3jKBKZLqf1ETzuPlsvpCN2UsxppJpMLsuOS51GDdPZVQ7o3v5ytK1RJ0mQhiKW4wSESp2lEfLdw0bRvs7LuUuYQ167kLIf4GqdpVJXRBOS4SJbp8UiCdi6ygVptk/jqoyP2ZK+m9JX1z8TLVIBxz/MqF45g==)

## 💿 Install & Usage

```sh
npm install --save-dev eslint eslint-plugin-n
```

Migrating from `eslint-plugin-node`? See the [migration guide](docs/migration.md).

| Version | Supported Node.js | Supported ESLint Version | Status |
|---------|-------------------|---------------------------|--------|
| 18.x   | `^20.19.0 \|\| ^22.13.0 \|\| >=24.0.0`  | `>=8.57.1`          | 🏃‍♂️actively maintained |
| 17.x   | `^18.18.0 \|\| ^20.9.0 \|\| >=21.1.0`  | `>=8.23.0`          | 🏃‍♂️actively maintained |
| 16.x   | `>=16.0.0`   | `>=7.0.0`          | ⚠️EOL |
| 15.x   | `>=12.22.0`  | `>=7.0.0`          | ⚠️EOL |

**Note:** It recommends a use of [the "engines" field of package.json](https://docs.npmjs.com/files/package.json#engines). The "engines" field is used by `n/no-unsupported-features/*` rules.

### [`eslint.config.js`](https://eslint.org/docs/latest/use/configure/configuration-files)

```js
import node from "eslint-plugin-n"
import {defineConfig} from "eslint/config"

export default defineConfig([
    {
        plugins: {n: node},
        extends: ["n/recommended-module"],
    }
])
```

To setup without the recommended configs, you'll need to add the plugin:

```js
import node from "eslint-plugin-n"
import {defineConfig} from "eslint/config"

export default defineConfig([
    {
        plugins: {n: node},
        rules: {
            "n/no-unsupported-features/es-builtins": "error",
        },
    }
])
```

**package.json** (An example)

```json
{
    "name": "your-module",
    "version": "1.0.0",
    "type": "commonjs",
    "engines": {
        "node": ">=8.10.0"
    }
}
```

### Configured Node.js version range

The rules get the supported Node.js version range from the following, falling back to the next if unspecified:

1. Rule configuration `version`
2. ESLint [shared setting](http://eslint.org/docs/user-guide/configuring.html#adding-shared-settings) `node.version`
3. `package.json` [`engines`] field
4. `package.json` [`devEngines.runtime`](https://docs.npmjs.com/cli/v11/configuring-npm/package-json#devengines) field (when `name` is `"node"`)
5. `>=16.0.0`

If you omit both the [engines] field and the `devEngines.runtime` field (with `name` set to `"node"`), this rule chooses `>=16.0.0` as the configured Node.js version since `16` is the maintained lts (see also [Node.js Release Working Group](https://github.com/nodejs/Release#readme)).

For Node.js packages, using the [`engines`] field is recommended because it's the official way to indicate support:

```json
{
    "name": "your-module",
    "version": "1.0.0",
    "engines": {
        "node": ">=16.0.0"
    }
}
```

For [Shareable Configs](https://eslint.org/docs/latest/developer-guide/shareable-configs) or packages with a different development environment (e.g. pre-compiled, web package, etc.), you can configure ESLint with `settings.node.version` to specify support.

## 📖 Rules

<!-- begin auto-generated rules list -->

💼 [Configurations](https://github.com/eslint-community/eslint-plugin-n#-configs) enabled in.\
🟢 Set in the `recommended-module` [configuration](https://github.com/eslint-community/eslint-plugin-n#-configs).\
✅ Set in the `recommended-script` [configuration](https://github.com/eslint-community/eslint-plugin-n#-configs).\
🔧 Automatically fixable by the [`--fix` CLI option](https://eslint.org/docs/user-guide/command-line-interface#--fix).\
❌ Deprecated.

| Name                                                                                         | Description                                                                           | 💼   | 🔧 | ❌  |
| :------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------ | :--- | :- | :- |
| [callback-return](docs/rules/callback-return.md)                                             | require `return` statements after callbacks                                           |      |    |    |
| [exports-style](docs/rules/exports-style.md)                                                 | enforce either `module.exports` or `exports`                                          |      | 🔧 |    |
| [file-extension-in-import](docs/rules/file-extension-in-import.md)                           | enforce the style of file extensions in `import` declarations                         |      | 🔧 |    |
| [global-require](docs/rules/global-require.md)                                               | require `require()` calls to be placed at top-level module scope                      |      |    |    |
| [handle-callback-err](docs/rules/handle-callback-err.md)                                     | require error handling in callbacks                                                   |      |    |    |
| [hashbang](docs/rules/hashbang.md)                                                           | require correct usage of hashbang                                                     | 🟢 ✅ | 🔧 |    |
| [no-callback-literal](docs/rules/no-callback-literal.md)                                     | enforce Node.js-style error-first callback pattern is followed                        |      |    |    |
| [no-deprecated-api](docs/rules/no-deprecated-api.md)                                         | disallow deprecated APIs                                                              | 🟢 ✅ |    |    |
| [no-exports-assign](docs/rules/no-exports-assign.md)                                         | disallow the assignment to `exports`                                                  | 🟢 ✅ |    |    |
| [no-extraneous-import](docs/rules/no-extraneous-import.md)                                   | disallow `import` declarations which import extraneous modules                        | 🟢 ✅ |    |    |
| [no-extraneous-require](docs/rules/no-extraneous-require.md)                                 | disallow `require()` expressions which import extraneous modules                      | 🟢 ✅ |    |    |
| [no-hide-core-modules](docs/rules/no-hide-core-modules.md)                                   | disallow third-party modules which are hiding core modules                            |      |    | ❌  |
| [no-missing-import](docs/rules/no-missing-import.md)                                         | disallow `import` declarations which import missing modules                           | 🟢 ✅ |    |    |
| [no-missing-require](docs/rules/no-missing-require.md)                                       | disallow `require()` expressions which import missing modules                         | 🟢 ✅ |    |    |
| [no-mixed-requires](docs/rules/no-mixed-requires.md)                                         | disallow `require` calls to be mixed with regular variable declarations               |      |    |    |
| [no-new-require](docs/rules/no-new-require.md)                                               | disallow `new` operators with calls to `require`                                      |      |    |    |
| [no-path-concat](docs/rules/no-path-concat.md)                                               | disallow string concatenation with `__dirname`, `__filename`, and `import.meta` paths |      |    |    |
| [no-process-env](docs/rules/no-process-env.md)                                               | disallow the use of `process.env`                                                     |      |    |    |
| [no-process-exit](docs/rules/no-process-exit.md)                                             | disallow the use of `process.exit()`                                                  | 🟢 ✅ |    |    |
| [no-restricted-import](docs/rules/no-restricted-import.md)                                   | disallow specified modules when loaded by `import` declarations                       |      |    |    |
| [no-restricted-require](docs/rules/no-restricted-require.md)                                 | disallow specified modules when loaded by `require`                                   |      |    |    |
| [no-sync](docs/rules/no-sync.md)                                                             | disallow synchronous methods                                                          |      |    |    |
| [no-top-level-await](docs/rules/no-top-level-await.md)                                       | disallow top-level `await` in published modules                                       |      |    |    |
| [no-unpublished-bin](docs/rules/no-unpublished-bin.md)                                       | disallow `bin` files that npm ignores                                                 |      |    |    |
| [no-unpublished-import](docs/rules/no-unpublished-import.md)                                 | disallow `import` declarations which import private modules                           | 🟢 ✅ |    |    |
| [no-unpublished-require](docs/rules/no-unpublished-require.md)                               | disallow `require()` expressions which import private modules                         | 🟢 ✅ |    |    |
| [no-unsupported-features/es-builtins](docs/rules/no-unsupported-features/es-builtins.md)     | disallow unsupported ECMAScript built-ins on the specified version                    | 🟢 ✅ |    |    |
| [no-unsupported-features/es-syntax](docs/rules/no-unsupported-features/es-syntax.md)         | disallow unsupported ECMAScript syntax on the specified version                       | 🟢 ✅ |    |    |
| [no-unsupported-features/node-builtins](docs/rules/no-unsupported-features/node-builtins.md) | disallow unsupported Node.js built-in APIs on the specified version                   | 🟢 ✅ |    |    |
| [prefer-global/buffer](docs/rules/prefer-global/buffer.md)                                   | enforce either `Buffer` or `require("buffer").Buffer`                                 |      |    |    |
| [prefer-global/console](docs/rules/prefer-global/console.md)                                 | enforce either `console` or `require("console")`                                      |      |    |    |
| [prefer-global/crypto](docs/rules/prefer-global/crypto.md)                                   | enforce either `crypto` or `require("crypto").webcrypto`                              |      |    |    |
| [prefer-global/process](docs/rules/prefer-global/process.md)                                 | enforce either `process` or `require("process")`                                      |      |    |    |
| [prefer-global/text-decoder](docs/rules/prefer-global/text-decoder.md)                       | enforce either `TextDecoder` or `require("util").TextDecoder`                         |      |    |    |
| [prefer-global/text-encoder](docs/rules/prefer-global/text-encoder.md)                       | enforce either `TextEncoder` or `require("util").TextEncoder`                         |      |    |    |
| [prefer-global/timers](docs/rules/prefer-global/timers.md)                                   | enforce either global timer functions or `require("timers")`                          |      |    |    |
| [prefer-global/url](docs/rules/prefer-global/url.md)                                         | enforce either `URL` or `require("url").URL`                                          |      |    |    |
| [prefer-global/url-search-params](docs/rules/prefer-global/url-search-params.md)             | enforce either `URLSearchParams` or `require("url").URLSearchParams`                  |      |    |    |
| [prefer-import/assert-strict](docs/rules/prefer-import/assert-strict.md)                     | enforce using `node:assert/strict` instead of `node:assert`.                          |      |    |    |
| [prefer-node-protocol](docs/rules/prefer-node-protocol.md)                                   | enforce using the `node:` protocol when importing Node.js builtin modules.            |      | 🔧 |    |
| [prefer-process-get-builtin-module](docs/rules/prefer-process-get-builtin-module.md)         | enforce using `process.getBuiltinModule()` to load Node.js built-in modules           |      |    |    |
| [prefer-promises/dns](docs/rules/prefer-promises/dns.md)                                     | enforce `require("dns").promises`                                                     |      |    |    |
| [prefer-promises/fs](docs/rules/prefer-promises/fs.md)                                       | enforce `require("fs").promises`                                                      |      |    |    |
| [process-exit-as-throw](docs/rules/process-exit-as-throw.md)                                 | require that `process.exit()` expressions use the same code path as `throw`           | 🟢 ✅ |    |    |
| [shebang](docs/rules/shebang.md)                                                             | require correct usage of hashbang                                                     |      | 🔧 | ❌  |

<!-- end auto-generated rules list -->

## 🔧 Configs

<!-- begin auto-generated configs list -->

|    | Name                 |
| :- | :------------------- |
| 🟢 | `recommended-module` |
| ✅  | `recommended-script` |

<!-- end auto-generated configs list -->

About each config:

- `recommended-module`: Considers all files as ES Modules.
- `recommended-script`: Considers all files as CommonJS.
- `recommended`: Considers both CommonJS and ES Modules. If [`"type":"module"` field](https://medium.com/@nodejs/announcing-a-new-experimental-modules-1be8d2d6c2ff#b023) existed in package.json then it considers files as ES Modules. Otherwise it considers files as CommonJS. In addition, it considers `*.mjs` files as ES Modules and `*.cjs` files as CommonJS.
- `all`: enables all of the rules shipped with the package. This configuration is **not recommended** for production use because it may change with every minor and major version. Use at your own risk.

These preset configs:

- enable [no-process-exit](http://eslint.org/docs/rules/no-process-exit) rule because [the official document](https://nodejs.org/api/process.html#process_process_exit_code) does not recommend a use of `process.exit()`.
- enable plugin rules indicated by emojis in the [rules table](#-rules).
- add `{ecmaVersion: 2021}` and etc into `parserOptions`.
- add proper globals into `globals`.
- add this plugin into `plugins`.

## 👫 FAQ

- Q: The `no-missing-import` / `no-missing-require` rules don't work with nested folders in SublimeLinter-eslint
- A: See [context.getFilename() in rule returns relative path](https://github.com/roadhump/SublimeLinter-eslint#contextgetfilename-in-rule-returns-relative-path) in the SublimeLinter-eslint FAQ.

- Q: How to use the eslint config with mixed commonjs and es modules?
- A: You can use the new exported flat config `mixed-esm-and-cjs`, an example:

```js
import node from "eslint-plugin-n"
import {defineConfig} from "eslint/config"

export default defineConfig([
    {
        plugins: {n: node},
        extends: ["n/mixed-esm-and-cjs"],
    },
])
```

## 🚥 Semantic Versioning Policy

`eslint-plugin-n` follows [semantic versioning](http://semver.org/) and [ESLint's Semantic Versioning Policy](https://github.com/eslint/eslint#semantic-versioning-policy).

- Patch release (intended to not break your lint build)
  - A bug fix in a rule that results in it reporting fewer errors.
  - Improvements to documentation.
  - Non-user-facing changes such as refactoring code, adding, deleting, or modifying tests, and increasing test coverage.
  - Re-releasing after a failed release (i.e., publishing a release that doesn't work for anyone).
- Minor release (might break your lint build)
  - A bug fix in a rule that results in it reporting more errors.
  - A new rule is created.
  - A new option to an existing rule is created.
  - An existing rule is deprecated.
- Major release (likely to break your lint build)
  - A support for old Node version is dropped.
  - A support for old ESLint version is dropped.
  - An existing rule is changed in it reporting more errors.
  - An existing rule is removed.
  - An existing option of a rule is removed.
  - An existing config is updated.

Deprecated rules follow ESLint's [deprecation policy](https://eslint.org/docs/user-guide/rule-deprecation).

## 📰 Changelog

- [GitHub Releases](https://github.com/eslint-community/eslint-plugin-n/releases)

## ❤️ Contributing

Welcome contributing!

Please use GitHub's Issues/PRs.

### Development Tools

- `npm test` runs tests and measures coverage.
- `npm run coverage` shows the coverage result of `npm test` command.
- `npm run clean` removes the coverage result of `npm test` command.
