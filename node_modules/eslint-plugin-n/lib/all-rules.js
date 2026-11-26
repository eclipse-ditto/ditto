/**
 * @fileoverview All rules in the plugin.
 * @author aladdin-add<weiran.zsd@outlook.com>
 * TODO: maybe auto-generated in the future?
 */

import callbackReturn from "./rules/callback-return.js"
import exportsStyle from "./rules/exports-style.js"
import fileExtensionInImport from "./rules/file-extension-in-import.js"
import globalRequire from "./rules/global-require.js"
import handleCallbackErr from "./rules/handle-callback-err.js"
import noCallbackLiteral from "./rules/no-callback-literal.js"
import noDeprecatedApi from "./rules/no-deprecated-api.js"
import noExportsAssign from "./rules/no-exports-assign.js"
import noExtraneousImport from "./rules/no-extraneous-import.js"
import noExtraneousRequire from "./rules/no-extraneous-require.js"
import noMissingImport from "./rules/no-missing-import.js"
import noMissingRequire from "./rules/no-missing-require.js"
import noMixedRequires from "./rules/no-mixed-requires.js"
import noNewRequire from "./rules/no-new-require.js"
import noPathConcat from "./rules/no-path-concat.js"
import noProcessEnv from "./rules/no-process-env.js"
import noProcessExit from "./rules/no-process-exit.js"
import noRestrictedImport from "./rules/no-restricted-import.js"
import noRestrictedRequire from "./rules/no-restricted-require.js"
import noSync from "./rules/no-sync.js"
import noTopLevelAwait from "./rules/no-top-level-await.js"
import noUnpublishedBin from "./rules/no-unpublished-bin.js"
import noUnpublishedImport from "./rules/no-unpublished-import.js"
import noUnpublishedRequire from "./rules/no-unpublished-require.js"
import noUnsupportedFeaturesEsBuiltins from "./rules/no-unsupported-features/es-builtins.js"
import noUnsupportedFeaturesEsSyntax from "./rules/no-unsupported-features/es-syntax.js"
import noUnsupportedFeaturesNodeBuiltins from "./rules/no-unsupported-features/node-builtins.js"
import preferGlobalBuffer from "./rules/prefer-global/buffer.js"
import preferGlobalConsole from "./rules/prefer-global/console.js"
import preferGlobalCrypto from "./rules/prefer-global/crypto.js"
import preferGlobalProcess from "./rules/prefer-global/process.js"
import preferGlobalTextDecoder from "./rules/prefer-global/text-decoder.js"
import preferGlobalTextEncoder from "./rules/prefer-global/text-encoder.js"
import preferGlobalUrlSearchParams from "./rules/prefer-global/url-search-params.js"
import preferGlobalUrl from "./rules/prefer-global/url.js"
import preferGlobalTimers from "./rules/prefer-global/timers.js"
import preferImportAssertStrict from "./rules/prefer-import/assert-strict.js"
import preferNodeProtocol from "./rules/prefer-node-protocol.js"
import preferProcessGetBuiltinModule from "./rules/prefer-process-get-builtin-module.js"
import preferPromisesDns from "./rules/prefer-promises/dns.js"
import preferPromisesFs from "./rules/prefer-promises/fs.js"
import processExitAsThrow from "./rules/process-exit-as-throw.js"
import hashbang from "./rules/hashbang.js"
import noHideCoreModules from "./rules/no-hide-core-modules.js"
import shebang from "./rules/shebang.js"

/** @type {Record<string, import('./rules/rule-module.js').RuleModule>} */
const allRules = {
    "callback-return": callbackReturn,
    "exports-style": exportsStyle,
    "file-extension-in-import": fileExtensionInImport,
    "global-require": globalRequire,
    "handle-callback-err": handleCallbackErr,
    "no-callback-literal": noCallbackLiteral,
    "no-deprecated-api": noDeprecatedApi,
    "no-exports-assign": noExportsAssign,
    "no-extraneous-import": noExtraneousImport,
    "no-extraneous-require": noExtraneousRequire,
    "no-missing-import": noMissingImport,
    "no-missing-require": noMissingRequire,
    "no-mixed-requires": noMixedRequires,
    "no-new-require": noNewRequire,
    "no-path-concat": noPathConcat,
    "no-process-env": noProcessEnv,
    "no-process-exit": noProcessExit,
    "no-restricted-import": noRestrictedImport,
    "no-restricted-require": noRestrictedRequire,
    "no-sync": noSync,
    "no-top-level-await": noTopLevelAwait,
    "no-unpublished-bin": noUnpublishedBin,
    "no-unpublished-import": noUnpublishedImport,
    "no-unpublished-require": noUnpublishedRequire,
    "no-unsupported-features/es-builtins": noUnsupportedFeaturesEsBuiltins,
    "no-unsupported-features/es-syntax": noUnsupportedFeaturesEsSyntax,
    "no-unsupported-features/node-builtins": noUnsupportedFeaturesNodeBuiltins,
    "prefer-global/buffer": preferGlobalBuffer,
    "prefer-global/console": preferGlobalConsole,
    "prefer-global/crypto": preferGlobalCrypto,
    "prefer-global/process": preferGlobalProcess,
    "prefer-global/text-decoder": preferGlobalTextDecoder,
    "prefer-global/text-encoder": preferGlobalTextEncoder,
    "prefer-global/url-search-params": preferGlobalUrlSearchParams,
    "prefer-global/url": preferGlobalUrl,
    "prefer-global/timers": preferGlobalTimers,
    "prefer-import/assert-strict": preferImportAssertStrict,
    "prefer-node-protocol": preferNodeProtocol,
    "prefer-process-get-builtin-module": preferProcessGetBuiltinModule,
    "prefer-promises/dns": preferPromisesDns,
    "prefer-promises/fs": preferPromisesFs,
    "process-exit-as-throw": processExitAsThrow,
    hashbang: hashbang,
    "no-hide-core-modules": noHideCoreModules,
    shebang: shebang,
}

export default allRules
