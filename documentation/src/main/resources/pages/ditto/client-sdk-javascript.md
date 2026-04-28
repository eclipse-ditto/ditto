---
title: JavaScript SDK
keywords:
tags: [client_sdk]
permalink: client-sdk-javascript.html
---

You use the Ditto JavaScript SDK to work with the Ditto HTTP API and WebSocket API from browser and Node.js environments.

{% include callout.html content="**TL;DR**: Install `@eclipse-ditto/ditto-javascript-client-dom` for browsers or `@eclipse-ditto/ditto-javascript-client-node` for Node.js, then use the client to manage Things, subscribe to events, and send messages." type="primary" %}

## Overview

The JavaScript SDK provides separate packages for different environments:

| Package | Environment | Description |
|---------|-------------|-------------|
| [`@eclipse-ditto/ditto-javascript-client-dom`](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/lib/dom/README.md) | Browser (DOM) | Uses browser-native HTTP and WebSocket |
| [`@eclipse-ditto/ditto-javascript-client-node`](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/lib/node/README.md) | Node.js | Uses Node.js HTTP and WebSocket |
| [`@eclipse-ditto/ditto-javascript-client-api`](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/lib/api/README.md) | Any | API-only -- build your own client implementation |

All released versions are published on [npmjs.com](https://www.npmjs.com/~eclipse_ditto).

## Getting started

### Installation

Install the package for your environment:

```bash
# For browser applications:
npm install @eclipse-ditto/ditto-javascript-client-dom

# For Node.js applications:
npm install @eclipse-ditto/ditto-javascript-client-node
```

### Compatibility

The JavaScript SDK tracks the same major version as Eclipse Ditto. The latest release covers as much API functionality as possible for the corresponding Ditto version.

## Building from source

```bash
npm install
npm run build
npm run lint
npm test
```

### Troubleshooting build issues

If you encounter build errors, clean everything and start fresh:

```bash
npm run clean
# Delete node_modules in the root folder
npm install
npm run build
```

The build process uses [lerna](https://github.com/lerna/lerna) for multi-package management and [rollup.js](https://rollupjs.org/) for generating multiple module formats (IIFE, ES Module, CommonJS). During install and build, lerna symlinks the `api` dependency into the `dom` and `node` packages.

## Further reading

* [@eclipse-ditto/ditto-javascript-client-dom README](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/lib/dom/README.md) -- browser usage details
* [@eclipse-ditto/ditto-javascript-client-node README](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/lib/node/README.md) -- Node.js usage details
* [@eclipse-ditto/ditto-javascript-client-api README](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/lib/api/README.md) -- API package details
* [HTTP API overview](httpapi-overview.html) -- the REST API the SDK wraps
* [WebSocket binding](httpapi-protocol-bindings-websocket.html) -- the WebSocket transport
