"use strict";
// Generates the list of deprecations from spec/deprecations.yaml in the
// language repo.
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
exports.getDeprecations = getDeprecations;
const fs = __importStar(require("fs"));
const yaml_1 = require("yaml");
const yamlFile = 'build/sass/spec/deprecations.yaml';
/**
 * Converts a version string in the form X.Y.Z to be code calling the Version
 * constructor, or null if the string is undefined.
 */
function toVersionCode(version) {
    if (!version)
        return 'null';
    const match = version.match(/^(\d+)\.(\d+)\.(\d+)$/);
    if (match === null) {
        throw new Error(`Invalid version ${version}`);
    }
    return `new Version(${match[1]}, ${match[2]}, ${match[3]})`;
}
/**
 * Generates the list of deprecations based on the YAML file in the language
 * repo.
 */
async function getDeprecations(outDirectory) {
    const yamlText = fs.readFileSync(yamlFile, 'utf8');
    const deprecations = (0, yaml_1.parse)(yamlText);
    let tsText = "import {Deprecations} from './sass';\n" +
        "import {Version} from '../version';\n\n" +
        'export const deprecations: Deprecations = {\n';
    for (const [id, deprecation] of Object.entries(deprecations)) {
        const key = id.includes('-') ? `'${id}'` : id;
        const dartSass = deprecation['dart-sass'];
        tsText +=
            `  ${key}: {\n` +
                `    id: '${id}',\n` +
                `    description: '${deprecation.description}',\n` +
                `    status: '${dartSass.status}',\n` +
                `    deprecatedIn: ${toVersionCode(dartSass.deprecated)},\n` +
                `    obsoleteIn: ${toVersionCode(dartSass.obsolete)},\n` +
                '  },\n';
    }
    tsText +=
        "  'user-authored': {\n" +
            "    id: 'user-authored',\n" +
            "    status: 'user',\n" +
            '    deprecatedIn: null,\n' +
            '    obsoleteIn: null,\n' +
            '  },\n' +
            '}\n';
    fs.writeFileSync(`${outDirectory}/deprecations.ts`, tsText);
}
//# sourceMappingURL=get-deprecations.js.map