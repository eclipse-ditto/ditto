"use strict";
// Copyright 2022 Google Inc. Use of this source code is governed by an
// MIT-style license that can be found in the LICENSE file or at
// https://opensource.org/licenses/MIT.
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.getLanguageRepo = getLanguageRepo;
const p = __importStar(require("path"));
const shell = __importStar(require("shelljs"));
const utils = __importStar(require("./utils"));
/**
 * Downloads the Sass language repo and buids the Embedded Sass protocol
 * definition.
 *
 * Can check out and build the source from a Git `ref` or build from the source
 * at `path`. By default, checks out the latest revision from GitHub.
 */
async function getLanguageRepo(outPath, options) {
    if (!options || 'ref' in options) {
        utils.fetchRepo({
            repo: 'sass',
            outPath: utils.BUILD_PATH,
            ref: options?.ref ?? 'main',
        });
    }
    else {
        await utils.cleanDir('build/sass');
        await utils.link(options.path, 'build/sass');
    }
    // Workaround for https://github.com/shelljs/shelljs/issues/198
    // This file is a symlink which gets messed up by `shell.cp` (called from
    // `utils.link`) on Windows.
    if (process.platform === 'win32')
        shell.rm('build/sass/spec/README.md');
    await utils.link('build/sass/js-api-doc', p.join(outPath, 'sass'));
    buildEmbeddedProtocol();
}
// Builds the embedded proto into a TS file.
function buildEmbeddedProtocol() {
    const version = shell.exec('npx buf --version', { silent: true }).stdout.trim();
    console.log(`Building TS with buf ${version}.`);
    shell.exec('npx buf generate');
}
//# sourceMappingURL=get-language-repo.js.map