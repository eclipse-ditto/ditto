"use strict"

const { READ } = require("@eslint-community/eslint-utils")

/** @type {import('../types.js').SupportVersionTraceMap} */
const process = {
    allowedNodeEnvironmentFlags: { [READ]: { supported: ["10.10.0"] } },
    availableMemory: { [READ]: { experimental: ["22.0.0", "20.13.0"] } },
    arch: { [READ]: { supported: ["0.5.0"] } },
    argv: { [READ]: { supported: ["0.1.27"] } },
    argv0: { [READ]: { supported: ["6.4.0"] } },
    channel: { [READ]: { supported: ["7.1.0"] } },
    config: { [READ]: { supported: ["0.7.7"] } },
    connected: { [READ]: { supported: ["0.7.2"] } },
    debugPort: { [READ]: { supported: ["0.7.2"] } },
    env: { [READ]: { supported: ["0.1.27"] } },
    execArgv: { [READ]: { supported: ["0.7.7"] } },
    execPath: { [READ]: { supported: ["0.1.100"] } },
    exitCode: { [READ]: { supported: ["0.11.8"] } },
    finalization: {
        register: { [READ]: { experimental: ["22.5.0"] } },
        registerBeforeExit: { [READ]: { experimental: ["22.5.0"] } },
        unregister: { [READ]: { experimental: ["22.5.0"] } },
    },
    getBuiltinModule: { [READ]: { supported: ["22.3.0", "20.16.0"] } },
    mainModule: {
        [READ]: {
            supported: ["0.1.17"],
            deprecated: ["14.0.0"],
        },
    },
    noDeprecation: { [READ]: { supported: ["0.8.0"] } },
    permission: { [READ]: { supported: ["20.0.0"] } },
    pid: { [READ]: { supported: ["0.1.15"] } },
    platform: { [READ]: { supported: ["0.1.16"] } },
    ppid: { [READ]: { supported: ["9.2.0", "8.10.0", "6.13.0"] } },
    release: { [READ]: { supported: ["3.0.0"] } },
    report: {
        [READ]: {
            experimental: ["11.8.0"],
            supported: ["13.12.0", "12.17.0"],
        },
    },
    sourceMapsEnabled: { [READ]: { experimental: ["20.7.0"] } },
    stdin: {
        [READ]: { supported: ["0.1.3"] },

        // tty.ReadStream
        isRaw: { [READ]: { supported: ["0.7.7"] } },
        isTTY: { [READ]: { supported: ["0.5.8"] } },
        setRawMode: { [READ]: { supported: ["0.7.7"] } },
    },
    stdout: {
        [READ]: { supported: ["0.1.3"] },

        // tty.WriteStream
        clearLine: { [READ]: { supported: ["0.7.7"] } },
        clearScreenDown: { [READ]: { supported: ["0.7.7"] } },
        columns: { [READ]: { supported: ["0.7.7"] } },
        cursorTo: { [READ]: { supported: ["0.7.7"] } },
        getColorDepth: { [READ]: { supported: ["9.9.0"] } },
        getWindowSize: { [READ]: { supported: ["0.7.7"] } },
        hasColors: { [READ]: { supported: ["11.13.0", "10.16.0"] } },
        isTTY: { [READ]: { supported: ["0.5.8"] } },
        moveCursor: { [READ]: { supported: ["0.7.7"] } },
        rows: { [READ]: { supported: ["0.7.7"] } },
    },
    stderr: {
        [READ]: { supported: ["0.1.3"] },

        // tty.WriteStream
        clearLine: { [READ]: { supported: ["0.7.7"] } },
        clearScreenDown: { [READ]: { supported: ["0.7.7"] } },
        columns: { [READ]: { supported: ["0.7.7"] } },
        cursorTo: { [READ]: { supported: ["0.7.7"] } },
        getColorDepth: { [READ]: { supported: ["9.9.0"] } },
        getWindowSize: { [READ]: { supported: ["0.7.7"] } },
        hasColors: { [READ]: { supported: ["11.13.0", "10.16.0"] } },
        isTTY: { [READ]: { supported: ["0.5.8"] } },
        moveCursor: { [READ]: { supported: ["0.7.7"] } },
        rows: { [READ]: { supported: ["0.7.7"] } },
    },
    throwDeprecation: { [READ]: { supported: ["0.9.12"] } },
    title: { [READ]: { supported: ["0.1.104"] } },
    traceDeprecation: { [READ]: { supported: ["0.8.0"] } },
    version: { [READ]: { supported: ["0.1.3"] } },
    versions: { [READ]: { supported: ["0.2.0"] } },

    abort: { [READ]: { supported: ["0.7.0"] } },
    chdir: { [READ]: { supported: ["0.1.17"] } },
    constrainedMemory: { [READ]: { experimental: ["19.6.0", "18.15.0"] } },
    cpuUsage: { [READ]: { supported: ["6.1.0"] } },
    cwd: { [READ]: { supported: ["0.1.8"] } },
    disconnect: { [READ]: { supported: ["0.7.2"] } },
    dlopen: { [READ]: { supported: ["0.1.16"] } },
    emitWarning: { [READ]: { supported: ["6.0.0"] } },
    exit: { [READ]: { supported: ["0.1.13"] } },
    getActiveResourcesInfo: { [READ]: { experimental: ["17.3.0", "16.14.0"] } },
    getegid: { [READ]: { supported: ["2.0.0"] } },
    geteuid: { [READ]: { supported: ["2.0.0"] } },
    getgid: { [READ]: { supported: ["0.1.31"] } },
    getgroups: { [READ]: { supported: ["0.9.4"] } },
    getuid: { [READ]: { supported: ["0.1.28"] } },
    hasUncaughtExceptionCaptureCallback: { [READ]: { supported: ["9.3.0"] } },
    hrtime: {
        [READ]: { supported: ["0.7.6"] },
        bigint: { [READ]: { supported: ["10.7.0"] } },
    },
    initgroups: { [READ]: { supported: ["0.9.4"] } },
    kill: { [READ]: { supported: ["0.0.6"] } },
    loadEnvFile: { [READ]: { supported: ["20.12.0", "21.7.0"] } },
    memoryUsage: { [READ]: { supported: ["0.1.16"] } },
    rss: { [READ]: { supported: ["15.6.0", "14.18.0"] } },
    nextTick: { [READ]: { supported: ["0.1.26"] } },
    resourceUsage: { [READ]: { supported: ["12.6.0"] } },
    send: { [READ]: { supported: ["0.5.9"] } },
    setegid: { [READ]: { supported: ["2.0.0"] } },
    seteuid: { [READ]: { supported: ["2.0.0"] } },
    setgid: { [READ]: { supported: ["0.1.31"] } },
    setgroups: { [READ]: { supported: ["0.9.4"] } },
    setuid: { [READ]: { supported: ["0.1.28"] } },
    setSourceMapsEnabled: { [READ]: { experimental: ["16.6.0", "14.18.0"] } },
    setUncaughtExceptionCaptureCallback: { [READ]: { supported: ["9.3.0"] } },
    umask: { [READ]: { supported: ["0.1.19"] } },
    uptime: { [READ]: { supported: ["0.5.0"] } },
}

/** @type {import('../types.js').SupportVersionTraceMap} */
module.exports = {
    process: {
        [READ]: { supported: ["0.1.3"] },
        ...process,
    },
    "node:process": {
        [READ]: { supported: ["14.13.1", "12.20.0"] },
        ...process,
    },
}
