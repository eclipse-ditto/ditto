"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const tls = {
    rootCertificates: { [READ]: { supported: ["12.3.0"] } },
    DEFAULT_ECDH_CURVE: { [READ]: { supported: ["0.11.13"] } },
    DEFAULT_MAX_VERSION: { [READ]: { supported: ["11.4.0"] } },
    DEFAULT_MIN_VERSION: { [READ]: { supported: ["11.4.0"] } },
    DEFAULT_CIPHERS: { [READ]: { supported: ["19.8.0", "18.16.0"] } },
    checkServerIdentity: { [READ]: { supported: ["0.8.4"] } },
    connect: { [READ]: { supported: ["0.11.3"] } },
    createSecureContext: { [READ]: { supported: ["0.11.13"] } },
    createSecurePair: {
        [READ]: { supported: ["0.3.2"], deprecated: ["0.11.3"] },
    },
    createServer: { [READ]: { supported: ["0.3.2"] } },
    getCiphers: { [READ]: { supported: ["0.10.2"] } },
    SecureContext: { [READ]: { supported: ["0.11.13"] } },
    CryptoStream: { [READ]: { supported: ["0.3.4"], deprecated: ["0.11.3"] } },
    SecurePair: { [READ]: { supported: ["0.3.2"], deprecated: ["0.11.3"] } },
    Server: { [READ]: { supported: ["0.3.2"] } },
    TLSSocket: { [READ]: { supported: ["0.11.4"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    tls: {
        [READ]: { supported: ["0.3.2"] },
        ...tls,
    },
    "node:tls": {
        [READ]: { supported: ["14.13.1", "12.20.0"] },
        ...tls,
    },
}
