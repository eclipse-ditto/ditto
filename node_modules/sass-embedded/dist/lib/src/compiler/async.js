"use strict";
// Copyright 2024 Google LLC. Use of this source code is governed by an
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
exports.AsyncCompiler = void 0;
exports.initAsyncCompiler = initAsyncCompiler;
const child_process = __importStar(require("child_process"));
const rxjs_1 = require("rxjs");
const operators_1 = require("rxjs/operators");
const path = __importStar(require("path"));
const utils_1 = require("./utils");
const compiler_path_1 = require("../compiler-path");
const deprecations_1 = require("../deprecations");
const function_registry_1 = require("../function-registry");
const importer_registry_1 = require("../importer-registry");
const message_transformer_1 = require("../message-transformer");
const packet_transformer_1 = require("../packet-transformer");
const utils = __importStar(require("../utils"));
/**
 * Flag allowing the constructor passed by `initAsyncCompiler` so we can
 * differentiate and throw an error if the `AsyncCompiler` is constructed via
 * `new AsyncCompiler`.
 */
const initFlag = Symbol();
/** An asynchronous wrapper for the embedded Sass compiler */
class AsyncCompiler {
    /** The underlying process that's being wrapped. */
    process = (() => {
        let command = compiler_path_1.compilerCommand[0];
        let args = [...compiler_path_1.compilerCommand.slice(1), '--embedded'];
        const options = {
            // Use the command's cwd so the compiler survives the removal of the
            // current working directory.
            // https://github.com/sass/embedded-host-node/pull/261#discussion_r1438712923
            cwd: path.dirname(compiler_path_1.compilerCommand[0]),
            windowsHide: true,
        };
        // Node forbids launching .bat and .cmd without a shell due to CVE-2024-27980,
        // and DEP0190 forbids passing an argument list *with* shell: true. To work
        // around this, we have to manually concatenate the arguments.
        if (['.bat', '.cmd'].includes(path.extname(command).toLowerCase())) {
            command = `${command} ${args.join(' ')}`;
            args = [];
            options.shell = true;
        }
        return child_process.spawn(command, args, options);
    })();
    /** The next compilation ID. */
    compilationId = 1;
    /** A list of active compilations. */
    compilations = new Set();
    /** Whether the underlying compiler has already exited. */
    disposed = false;
    /** Reusable message transformer for all compilations.  */
    messageTransformer;
    /** The child process's exit event. */
    exit$ = new Promise(resolve => {
        this.process.on('exit', code => resolve(code));
    });
    /** The buffers emitted by the child process's stdout. */
    stdout$ = new rxjs_1.Observable(observer => {
        this.process.stdout.on('data', buffer => observer.next(buffer));
    }).pipe((0, operators_1.takeUntil)(this.exit$));
    /** The buffers emitted by the child process's stderr. */
    stderr$ = new rxjs_1.Observable(observer => {
        this.process.stderr.on('data', buffer => observer.next(buffer));
    }).pipe((0, operators_1.takeUntil)(this.exit$));
    /** Writes `buffer` to the child process's stdin. */
    writeStdin(buffer) {
        this.process.stdin.write(buffer);
    }
    /** Guards against using a disposed compiler. */
    throwIfDisposed() {
        if (this.disposed) {
            throw utils.compilerError('Async compiler has already been disposed');
        }
    }
    /**
     * Sends a compile request to the child process and returns a Promise that
     * resolves with the CompileResult. Rejects the promise if there were any
     * protocol or compilation errors.
     */
    async compileRequestAsync(request, importers, options) {
        const optionsKey = Symbol();
        deprecations_1.activeDeprecationOptions.set(optionsKey, options ?? {});
        try {
            const functions = new function_registry_1.FunctionRegistry(options?.functions);
            const dispatcher = (0, utils_1.createDispatcher)(this.compilationId++, this.messageTransformer, {
                handleImportRequest: request => importers.import(request),
                handleFileImportRequest: request => importers.fileImport(request),
                handleCanonicalizeRequest: request => importers.canonicalize(request),
                handleFunctionCallRequest: request => functions.call(request),
            });
            dispatcher.logEvents$.subscribe(event => (0, utils_1.handleLogEvent)(options, event));
            // Avoid `new Promise()` here because `dispatcher.sendCompilerequest` can
            // run its callback synchronously, so `compilation` needs to be assigned
            // and in `this.compilations` before we call it.
            const { promise: compilation, resolve, reject, } = (0, utils_1.promiseWithResolvers)();
            this.compilations.add(compilation);
            dispatcher.sendCompileRequest(request, (err, response) => {
                this.compilations.delete(compilation);
                // Reset the compilation ID when the compiler goes idle (no active
                // compilations) to avoid overflowing it.
                // https://github.com/sass/embedded-host-node/pull/261#discussion_r1429266794
                if (this.compilations.size === 0)
                    this.compilationId = 1;
                if (err) {
                    reject(err);
                }
                else {
                    resolve(response);
                }
            });
            return (0, utils_1.handleCompileResponse)(await compilation);
        }
        finally {
            deprecations_1.activeDeprecationOptions.delete(optionsKey);
        }
    }
    /** Initialize resources shared across compilations. */
    constructor(flag) {
        if (flag !== initFlag) {
            throw utils.compilerError('AsyncCompiler can not be directly constructed. ' +
                'Please use `sass.initAsyncCompiler()` instead');
        }
        this.stderr$.subscribe(data => process.stderr.write(data));
        const packetTransformer = new packet_transformer_1.PacketTransformer(this.stdout$, buffer => {
            this.writeStdin(buffer);
        });
        this.messageTransformer = new message_transformer_1.MessageTransformer(packetTransformer.outboundProtobufs$, packet => packetTransformer.writeInboundProtobuf(packet));
    }
    compileAsync(path, options) {
        this.throwIfDisposed();
        const importers = new importer_registry_1.ImporterRegistry(options);
        return this.compileRequestAsync((0, utils_1.newCompilePathRequest)(path, importers, options), importers, options);
    }
    compileStringAsync(source, options) {
        this.throwIfDisposed();
        const importers = new importer_registry_1.ImporterRegistry(options);
        return this.compileRequestAsync((0, utils_1.newCompileStringRequest)(source, importers, options), importers, options);
    }
    async dispose() {
        this.disposed = true;
        await Promise.all(this.compilations);
        this.process.stdin.end();
        await this.exit$;
    }
}
exports.AsyncCompiler = AsyncCompiler;
async function initAsyncCompiler() {
    return new AsyncCompiler(initFlag);
}
//# sourceMappingURL=async.js.map