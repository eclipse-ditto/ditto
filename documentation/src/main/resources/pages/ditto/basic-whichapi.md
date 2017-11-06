---
title: Which API to use?
keywords: API, HTTP, REST, websocket
tags: [model]
permalink: basic-whichapi.html
---

Ditto provides 2 ways to interact with:

* A REST-like HTTP API with a sophisticated resource layout, that allows to create, read, update and delete Things and the Thing's data.
* A JSON based WebSocket protocol.


## HTTP API or JSON based WebSocket protocol?

The 2 ways are **nearly equally powerful** and allow the same operations to work with the Thing's data, send messages to Things and receive messages from Things.

* The lightweight REST-like HTTP API can be used
    * on less powerful devices lacking a Java runtime or supporting other (scripting) languages like JavaScript, Python, C/C++,
    * and for developing Web-based user interfaces.
* The plain WebSocket channel proves useful for
    * gathering data streams from devices or massive data from another message broker
    * real-time device monitoring,
    * event-driven Web applications,
    * full duplex communication scenarios, etc.
   
    
## Comparison by feature

| Feature | Ditto protocol over WebSocket | Ditto REST-like HTTP API |
|---------|--------------------------------|---------------------------|
| Things management             | ✓     | ✓ |
| Features management           | ✓     | ✓ |
| Search Things                 | no    | ✓ |
| Count Things                  | no    | ✓ |
| Messages                      | ✓     | ✓ |
| Change Events	                | ✓     | ✓ |
| Access control via Policy	    | ✓     | ✓ (v2 only) |
| Access control via ACL        | ✓     | ✓ (v1 only) |


## Further aspects in which the interfaces differ

| Criteria            | Ditto protocol over WebSocket	| Ditto REST-like HTTP API |
|---------------------|---------------------------------|---------------------------|
| Programming language      | Almost any Web oriented programming language, e.g. Java, JavaScript, .NET | Almost any programming language, e.g. Java, JavaScript, NodeJS, .NET, Python, C/C++ |
| Connection paradigm       | Connection-oriented with an always open and persistent connection with only one-time handshake overhead for lowest latency and highest throughput | Connectionless protocol with lower permanent resource allocation on sporadic transactions |
| Channel security          | WSS WebSocket over Transport Layer Security | HTTPS HTTP over Transport Layer Security |
| Message exchange pattern  | non-blocking request - asynchronous response | blocking request - response |
| Authentication mechanism  | User authentication using: HTTP BASIC Authentication, JSON Web Token (JWT) issued OpenID connect provider | User authentication using: HTTP BASIC Authentication, JSON Web Token (JWT) issued OpenID connect provider |
