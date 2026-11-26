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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.nodePlatformToDartPlatform = nodePlatformToDartPlatform;
exports.nodeArchToDartArch = nodeArchToDartArch;
const extractZip = require("extract-zip");
const fs_1 = require("fs");
const p = __importStar(require("path"));
const tar_1 = require("tar");
const yargs_1 = __importDefault(require("yargs"));
const pkg = __importStar(require("../package.json"));
const utils = __importStar(require("./utils"));
const argv = (0, yargs_1.default)(process.argv.slice(2))
    .option('package', {
    type: 'string',
    description: 'Directory name under `npm` directory that contains optional dependencies.',
    demandOption: true,
    choices: Object.keys(pkg.optionalDependencies).map(name => name.split('sass-embedded-')[1]),
})
    .parseSync();
// Converts a Node-style platform name as returned by `process.platform` into a
// name used by Dart Sass. Throws if the operating system is not supported by
// Dart Sass Embedded.
function nodePlatformToDartPlatform(platform) {
    switch (platform) {
        case 'android':
            return 'android';
        case 'linux':
        case 'linux-musl':
            return 'linux';
        case 'darwin':
            return 'macos';
        case 'win32':
            return 'windows';
        default:
            throw Error(`Platform ${platform} is not supported.`);
    }
}
// Converts a Node-style architecture name as returned by `process.arch` into a
// name used by Dart Sass. Throws if the architecture is not supported by Dart
// Sass Embedded.
function nodeArchToDartArch(arch) {
    switch (arch) {
        case 'x64':
            return 'x64';
        case 'arm':
            return 'arm';
        case 'arm64':
            return 'arm64';
        case 'riscv64':
            return 'riscv64';
        default:
            throw Error(`Architecture ${arch} is not supported.`);
    }
}
// Get the platform's file extension for archives.
function getArchiveExtension(platform) {
    return platform === 'windows' ? '.zip' : '.tar.gz';
}
// Downloads the release for `repo` located at `assetUrl`, then unzips it into
// `outPath`.
async function downloadRelease(options) {
    console.log(`Downloading ${options.repo} release asset.`);
    const response = await fetch(options.assetUrl, {
        redirect: 'follow',
    });
    if (!response.ok) {
        throw Error(`Failed to download ${options.repo} release asset: ${response.statusText}`);
    }
    const releaseAsset = Buffer.from(await response.arrayBuffer());
    console.log(`Unzipping ${options.repo} release asset to ${options.outPath}.`);
    await utils.cleanDir(p.join(options.outPath, options.repo));
    const archiveExtension = options.assetUrl.endsWith('.zip')
        ? '.zip'
        : '.tar.gz';
    const zippedAssetPath = options.outPath + '/' + options.repo + archiveExtension;
    await fs_1.promises.writeFile(zippedAssetPath, releaseAsset);
    if (archiveExtension === '.zip') {
        await extractZip(zippedAssetPath, {
            dir: p.join(process.cwd(), options.outPath),
        });
    }
    else {
        (0, tar_1.extract)({
            file: zippedAssetPath,
            cwd: options.outPath,
            sync: true,
        });
    }
    await fs_1.promises.unlink(zippedAssetPath);
}
void (async () => {
    try {
        const version = pkg['compiler-version'];
        if (version.endsWith('-dev')) {
            throw Error("Can't release optional packages for a -dev compiler version.");
        }
        const optPkg = JSON.parse((await fs_1.promises.readFile(p.join('npm', argv.package, 'package.json'))).toString());
        if (optPkg.version !== pkg.version) {
            throw Error("Optional package's version does not match main package's version");
        }
        const sassDependencyVersion = optPkg.dependencies?.sass;
        if (sassDependencyVersion !== undefined) {
            if (sassDependencyVersion !== pkg.version) {
                throw Error("Optional package's sass dependency version does not match main package's version");
            }
            return;
        }
        const index = argv.package.lastIndexOf('-');
        const nodePlatform = argv.package.substring(0, index);
        const nodeArch = argv.package.substring(index + 1);
        const dartPlatform = nodePlatformToDartPlatform(nodePlatform);
        const dartArch = nodeArchToDartArch(nodeArch);
        const isMusl = nodePlatform === 'linux-musl';
        const outPath = p.join('npm', argv.package);
        await downloadRelease({
            repo: 'dart-sass',
            assetUrl: 'https://github.com/sass/dart-sass/releases/download/' +
                `${version}/dart-sass-${version}-` +
                `${dartPlatform}-${dartArch}${isMusl ? '-musl' : ''}` +
                `${getArchiveExtension(dartPlatform)}`,
            outPath,
        });
    }
    catch (error) {
        console.error(error);
        process.exitCode = 1;
    }
})();
//# sourceMappingURL=prepare-optional-release.js.map