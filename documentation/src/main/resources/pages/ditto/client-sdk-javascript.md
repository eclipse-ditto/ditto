---
title: Client SDK JavaScript
keywords: 
tags: [client_sdk]
permalink: client-sdk-javascript.html
---

A TypeScript library to facilitate working the the REST-like HTTP API and web socket API of Eclipse Ditto.

## How to use it
Install `@eclipse-ditto/ditto-javascript-client-dom` for the DOM (browser) implementation, 
`@eclipse-ditto/ditto-javascript-client-node` for the NodeJS implementation, or `@eclipse/ditto-javascript-client-api-ditto` for
the API and build your own client implementation.

More information can be found in the descriptions of the subpackages:
* [@eclipse-ditto/ditto-javascript-client-api](https://github.com/eclipse/ditto-clients/tree/master/javascript/lib/lib/api/README.md)
* [@eclipse-ditto/ditto-javascript-client-dom](https://github.com/eclipse/ditto-clients/tree/master/javascript/lib/lib/dom/README.md) 
* [@eclipse-ditto/ditto-javascript-client-node](https://github.com/eclipse/ditto-clients/tree/master/javascript/lib/lib/node/README.md)

## Compatibility with [Eclipse Ditto](https://github.com/eclipse/ditto)

The `@eclipse-ditto/ditto-javascript-client-<package>_<ditto-major-version>` modules
will be compatible with the `<ditto-major-version>`, so e.g. `@eclipse-ditto/ditto-javascript-client-<package>_1`
will be compatible with Eclipse Ditto 1.x. The javascript modules themselves use 
[semantic versioning](https://docs.npmjs.com/about-semantic-versioning) for their
api changes. All modules are released together, so if there is a version
`@eclipse-ditto/ditto-javascript-client-api_0:1.0.1`, there's also a release
of `@eclipse-ditto/ditto-javascript-client-dom_0:1.0.1`.

The newest release of the JavaScript client will always try to cover as much API
functionality of the same Eclipse Ditto major version as possible. There might
however be missing features for which we would be very happy to accept contributions.


## Coding
```
npm install
npm run build
npm run lint
npm test
# or npm run test:watch
```

## Troubleshooting
If you get strange errors, it would be best cleaning all dependencies and
starting from the beginning again:
```
npm run clean
# by hand delete node_modules in the root folder, or use a tool like rm, rimraf, etc.
npm install
npm run build
# ...
```
It is important to know that during install and build some extra processes
are triggered by e.g. lerna which will symlink the `api` dependency into 
the node_modules of `dom` and `node` packages.

## Internals
This project is using [lerna](https://github.com/lerna/lerna) to split up the
client into different packages. This way we can have standalone codeable 
subprojects (`api`, `dom` and `node`) but still are able to control dependencies,
build processes or release processes globally.

Furthermore we use [rollup.js](https://rollupjs.org/) for providing multiple
module types of the packages, e.g. the `api` will be published as IIFE,
ES Module and CommonJS module.

For automatically generating barrel files, [barrelsby](https://github.com/bencoveney/barrelsby)
is used during the build process.
