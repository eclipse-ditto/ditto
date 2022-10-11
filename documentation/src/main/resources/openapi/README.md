## Eclipse Ditto :: Documentation :: OpenAPI Specification

This folder contains the OpenAPI [OpenAPI](https://www.openapis.org) [Specification version 2](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md) documentation for Eclipse Ditto. 

You can view it as nicely rendered HTML by importing the file for API version 2 into the [Swagger Online Editor](https://editor.swagger.io).

### Extend/update api docs

To extend or update the OpenAPI of Ditto you can add or change the files in the `sources` directory.

### Build api docs

1. Install `swagger-cli`: `$ cd sources && npm install`
2. Build bundled docs:
```
// go to sources
$ cd sources

// run build
$ npm run build
```
