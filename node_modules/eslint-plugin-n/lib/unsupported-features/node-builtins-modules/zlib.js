"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const zlib = {
    constants: { [READ]: { supported: ["7.0.0"] } },
    crc32: { [READ]: { supported: ["22.2.0", "20.15.0"] } },
    createBrotliCompress: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    createBrotliDecompress: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    createDeflate: { [READ]: { supported: ["0.5.8"] } },
    createDeflateRaw: { [READ]: { supported: ["0.5.8"] } },
    createGunzip: { [READ]: { supported: ["0.5.8"] } },
    createGzip: { [READ]: { supported: ["0.5.8"] } },
    createInflate: { [READ]: { supported: ["0.5.8"] } },
    createInflateRaw: { [READ]: { supported: ["0.5.8"] } },
    createUnzip: { [READ]: { supported: ["0.5.8"] } },
    brotliCompress: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    brotliCompressSync: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    brotliDecompress: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    brotliDecompressSync: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    deflate: { [READ]: { supported: ["0.6.0"] } },
    deflateSync: { [READ]: { supported: ["0.11.12"] } },
    deflateRaw: { [READ]: { supported: ["0.6.0"] } },
    deflateRawSync: { [READ]: { supported: ["0.11.12"] } },
    gunzip: { [READ]: { supported: ["0.6.0"] } },
    gunzipSync: { [READ]: { supported: ["0.11.12"] } },
    gzip: { [READ]: { supported: ["0.6.0"] } },
    gzipSync: { [READ]: { supported: ["0.11.12"] } },
    inflate: { [READ]: { supported: ["0.6.0"] } },
    inflateSync: { [READ]: { supported: ["0.11.12"] } },
    inflateRaw: { [READ]: { supported: ["0.6.0"] } },
    inflateRawSync: { [READ]: { supported: ["0.11.12"] } },
    unzip: { [READ]: { supported: ["0.6.0"] } },
    unzipSync: { [READ]: { supported: ["0.11.12"] } },
    BrotliCompress: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    BrotliDecompress: { [READ]: { supported: ["11.7.0", "10.16.0"] } },
    Deflate: { [READ]: { supported: ["0.5.8"] } },
    DeflateRaw: { [READ]: { supported: ["0.5.8"] } },
    Gunzip: { [READ]: { supported: ["0.5.8"] } },
    Gzip: { [READ]: { supported: ["0.5.8"] } },
    Inflate: { [READ]: { supported: ["0.5.8"] } },
    InflateRaw: { [READ]: { supported: ["0.5.8"] } },
    Unzip: { [READ]: { supported: ["0.5.8"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    zlib: zlib,
    "node:zlib": {
        ...zlib,
        [READ]: { supported: ["14.13.1", "12.20.0"] },
    },
}
