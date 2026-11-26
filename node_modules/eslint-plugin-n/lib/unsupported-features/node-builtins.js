import { nodeGlobals as NodeBuiltinGlobals } from "./node-globals.js"
import { importMeta as NodeBuiltinImportMeta } from "./node-import-meta.js"

import assert from "./node-builtins-modules/assert.js"
import async_hooks from "./node-builtins-modules/async_hooks.js"
import buffer from "./node-builtins-modules/buffer.js"
import child_process from "./node-builtins-modules/child_process.js"
import cluster from "./node-builtins-modules/cluster.js"
import console from "./node-builtins-modules/console.js"
import crypto from "./node-builtins-modules/crypto.js"
import dgram from "./node-builtins-modules/dgram.js"
import diagnostics_channel from "./node-builtins-modules/diagnostics_channel.js"
import dns from "./node-builtins-modules/dns.js"
import domain from "./node-builtins-modules/domain.js"
import events from "./node-builtins-modules/events.js"
import fs from "./node-builtins-modules/fs.js"
import http2 from "./node-builtins-modules/http2.js"
import http from "./node-builtins-modules/http.js"
import https from "./node-builtins-modules/https.js"
import inspector from "./node-builtins-modules/inspector.js"
import module from "./node-builtins-modules/module.js"
import net from "./node-builtins-modules/net.js"
import os from "./node-builtins-modules/os.js"
import path from "./node-builtins-modules/path.js"
import perf_hooks from "./node-builtins-modules/perf_hooks.js"
import process from "./node-builtins-modules/process.js"
import punycode from "./node-builtins-modules/punycode.js"
import querystring from "./node-builtins-modules/querystring.js"
import readline from "./node-builtins-modules/readline.js"
import repl from "./node-builtins-modules/repl.js"
import sea from "./node-builtins-modules/sea.js"
import stream from "./node-builtins-modules/stream.js"
import string_decoder from "./node-builtins-modules/string_decoder.js"
import sqlite from "./node-builtins-modules/sqlite.js"
import test from "./node-builtins-modules/test.js"
import timers from "./node-builtins-modules/timers.js"
import tls from "./node-builtins-modules/tls.js"
import trace_events from "./node-builtins-modules/trace_events.js"
import tty from "./node-builtins-modules/tty.js"
import url from "./node-builtins-modules/url.js"
import util from "./node-builtins-modules/util.js"
import v8 from "./node-builtins-modules/v8.js"
import vm from "./node-builtins-modules/vm.js"
import wasi from "./node-builtins-modules/wasi.js"
import worker_threads from "./node-builtins-modules/worker_threads.js"
import zlib from "./node-builtins-modules/zlib.js"

/**
 * @type {import('./types.js').SupportVersionTraceMap}
 */
const NodeBuiltinModules = {
    ...assert,
    ...async_hooks,
    ...buffer,
    ...child_process,
    ...cluster,
    ...console,
    ...crypto,
    ...dgram,
    ...diagnostics_channel,
    ...dns,
    ...domain,
    ...events,
    ...fs,
    ...http2,
    ...http,
    ...https,
    ...inspector,
    ...module,
    ...net,
    ...os,
    ...path,
    ...perf_hooks,
    ...process,
    ...punycode,
    ...querystring,
    ...readline,
    ...repl,
    ...sea,
    ...stream,
    ...string_decoder,
    ...sqlite,
    ...test,
    ...timers,
    ...tls,
    ...trace_events,
    ...tty,
    ...url,
    ...util,
    ...v8,
    ...vm,
    ...wasi,
    ...worker_threads,
    ...zlib,
}

export { NodeBuiltinGlobals, NodeBuiltinModules, NodeBuiltinImportMeta }
