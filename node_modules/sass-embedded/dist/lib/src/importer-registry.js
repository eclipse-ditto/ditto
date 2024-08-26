"use strict";
// Copyright 2021 Google LLC. Use of this source code is governed by an
// MIT-style license that can be found in the LICENSE file or at
// https://opensource.org/licenses/MIT.
Object.defineProperty(exports, "__esModule", { value: true });
exports.ImporterRegistry = exports.NodePackageImporter = void 0;
const module_1 = require("module");
const p = require("path");
const url_1 = require("url");
const util_1 = require("util");
const canonicalize_context_1 = require("./canonicalize-context");
const utils = require("./utils");
const proto = require("./vendor/embedded_sass_pb");
const utils_1 = require("./utils");
const entryPointDirectoryKey = Symbol();
class NodePackageImporter {
    constructor(entryPointDirectory) {
        var _a;
        entryPointDirectory = entryPointDirectory
            ? p.resolve(entryPointDirectory)
            : ((_a = require.main) === null || _a === void 0 ? void 0 : _a.filename)
                ? p.dirname(require.main.filename)
                : // TODO: Find a way to use `import.meta.main` once
                    // https://github.com/nodejs/node/issues/49440 is done.
                    process.argv[1]
                        ? (0, module_1.createRequire)(process.argv[1]).resolve(process.argv[1])
                        : undefined;
        if (!entryPointDirectory) {
            throw new Error('The Node package importer cannot determine an entry point ' +
                'because `require.main.filename` is not defined. ' +
                'Please provide an `entryPointDirectory` to the `NodePackageImporter`.');
        }
        this[entryPointDirectoryKey] = entryPointDirectory;
    }
}
exports.NodePackageImporter = NodePackageImporter;
/**
 * A registry of importers defined in the host that can be invoked by the
 * compiler.
 */
class ImporterRegistry {
    constructor(options) {
        var _a, _b;
        /** A map from importer IDs to their corresponding importers. */
        this.importersById = new Map();
        /** A map from file importer IDs to their corresponding importers. */
        this.fileImportersById = new Map();
        /** The next ID to use for an importer. */
        this.id = 0;
        this.importers = ((_a = options === null || options === void 0 ? void 0 : options.importers) !== null && _a !== void 0 ? _a : [])
            .map(importer => this.register(importer))
            .concat(((_b = options === null || options === void 0 ? void 0 : options.loadPaths) !== null && _b !== void 0 ? _b : []).map(path => new proto.InboundMessage_CompileRequest_Importer({
            importer: { case: 'path', value: p.resolve(path) },
        })));
    }
    /** Converts an importer to a proto without adding it to `this.importers`. */
    register(importer) {
        var _a;
        const message = new proto.InboundMessage_CompileRequest_Importer();
        if (importer instanceof NodePackageImporter) {
            const importerMessage = new proto.NodePackageImporter();
            importerMessage.entryPointDirectory = importer[entryPointDirectoryKey];
            message.importer = {
                case: 'nodePackageImporter',
                value: importerMessage,
            };
        }
        else if ('canonicalize' in importer) {
            if ('findFileUrl' in importer) {
                throw new Error('Importer may not contain both canonicalize() and findFileUrl(): ' +
                    (0, util_1.inspect)(importer));
            }
            message.importer = { case: 'importerId', value: this.id };
            message.nonCanonicalScheme =
                typeof importer.nonCanonicalScheme === 'string'
                    ? [importer.nonCanonicalScheme]
                    : (_a = importer.nonCanonicalScheme) !== null && _a !== void 0 ? _a : [];
            this.importersById.set(this.id, importer);
        }
        else {
            message.importer = { case: 'fileImporterId', value: this.id };
            this.fileImportersById.set(this.id, importer);
        }
        this.id += 1;
        return message;
    }
    /** Handles a canonicalization request. */
    canonicalize(request) {
        const importer = this.importersById.get(request.importerId);
        if (!importer) {
            throw utils.compilerError('Unknown CanonicalizeRequest.importer_id');
        }
        const canonicalizeContext = new canonicalize_context_1.CanonicalizeContext(request.containingUrl ? new url_1.URL(request.containingUrl) : null, request.fromImport);
        return (0, utils_1.catchOr)(() => {
            return (0, utils_1.thenOr)(importer.canonicalize(request.url, canonicalizeContext), url => new proto.InboundMessage_CanonicalizeResponse({
                result: url === null
                    ? { case: undefined }
                    : { case: 'url', value: url.toString() },
                containingUrlUnused: !canonicalizeContext.containingUrlAccessed,
            }));
        }, error => new proto.InboundMessage_CanonicalizeResponse({
            result: { case: 'error', value: `${error}` },
        }));
    }
    /** Handles an import request. */
    import(request) {
        const importer = this.importersById.get(request.importerId);
        if (!importer) {
            throw utils.compilerError('Unknown ImportRequest.importer_id');
        }
        return (0, utils_1.catchOr)(() => {
            return (0, utils_1.thenOr)(importer.load(new url_1.URL(request.url)), result => {
                var _a, _b;
                if (!result)
                    return new proto.InboundMessage_ImportResponse();
                if (typeof result.contents !== 'string') {
                    throw Error(`Invalid argument (contents): must be a string but was: ${result.contents.constructor.name}`);
                }
                if (result.sourceMapUrl && !result.sourceMapUrl.protocol) {
                    throw Error('Invalid argument (sourceMapUrl): must be absolute but was: ' +
                        result.sourceMapUrl);
                }
                return new proto.InboundMessage_ImportResponse({
                    result: {
                        case: 'success',
                        value: new proto.InboundMessage_ImportResponse_ImportSuccess({
                            contents: result.contents,
                            syntax: utils.protofySyntax(result.syntax),
                            sourceMapUrl: (_b = (_a = result.sourceMapUrl) === null || _a === void 0 ? void 0 : _a.toString()) !== null && _b !== void 0 ? _b : '',
                        }),
                    },
                });
            });
        }, error => new proto.InboundMessage_ImportResponse({
            result: { case: 'error', value: `${error}` },
        }));
    }
    /** Handles a file import request. */
    fileImport(request) {
        const importer = this.fileImportersById.get(request.importerId);
        if (!importer) {
            throw utils.compilerError('Unknown FileImportRequest.importer_id');
        }
        const canonicalizeContext = new canonicalize_context_1.CanonicalizeContext(request.containingUrl ? new url_1.URL(request.containingUrl) : null, request.fromImport);
        return (0, utils_1.catchOr)(() => {
            return (0, utils_1.thenOr)(importer.findFileUrl(request.url, canonicalizeContext), url => {
                if (!url) {
                    return new proto.InboundMessage_FileImportResponse({
                        containingUrlUnused: !canonicalizeContext.containingUrlAccessed,
                    });
                }
                if (url.protocol !== 'file:') {
                    throw (`FileImporter ${(0, util_1.inspect)(importer)} returned non-file: URL ` +
                        +`"${url}" for URL "${request.url}".`);
                }
                return new proto.InboundMessage_FileImportResponse({
                    result: { case: 'fileUrl', value: url.toString() },
                    containingUrlUnused: !canonicalizeContext.containingUrlAccessed,
                });
            });
        }, error => new proto.InboundMessage_FileImportResponse({
            result: { case: 'error', value: `${error}` },
        }));
    }
}
exports.ImporterRegistry = ImporterRegistry;
//# sourceMappingURL=importer-registry.js.map