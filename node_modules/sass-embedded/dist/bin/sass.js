#!/usr/bin/env node
"use strict";
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
const child_process = __importStar(require("child_process"));
const path = __importStar(require("path"));
const compiler_path_1 = require("../lib/src/compiler-path");
// TODO npm/cmd-shim#152 and yarnpkg/berry#6422 - If and when the package
// managers support it, we should make this a proper shell script rather than a
// JS wrapper.
try {
    let command = compiler_path_1.compilerCommand[0];
    let args = [...compiler_path_1.compilerCommand.slice(1), ...process.argv.slice(2)];
    const options = {
        stdio: 'inherit',
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
    child_process.execFileSync(command, args, options);
}
catch (error) {
    if (error.code) {
        throw error;
    }
    else {
        process.exitCode = error.status;
    }
}
//# sourceMappingURL=sass.js.map