---
title: SSH Tunneling
keywords: ssh, tunnel, tunneling, port forwarding
tags: [connectivity]
permalink: connectivity-ssh-tunneling.html
---

You use SSH tunneling to reach endpoints that are not directly accessible, by routing the connection through an SSH server.

{% include callout.html content="**TL;DR**: Add an `sshTunnel` section to your connection configuration with the SSH server URI and credentials. Ditto establishes local port forwarding and connects to the target endpoint through the tunnel." type="primary" %}

## Overview

A managed connection can establish an SSH tunnel using
[TCP/IP port forwarding (RFC 4254)](https://tools.ietf.org/html/rfc4254#section-7) to connect to
endpoints that are only reachable via an SSH server. Ditto first opens the SSH tunnel, then connects
to the actual endpoint through it.

The tunneling supports:

* Password authentication
* Public key authentication
* Host validation using public key fingerprints

{% include note.html content="SSH tunneling can impact transmission performance compared to a direct connection." %}

## Configuration

Add an `sshTunnel` section to your connection configuration:

```json
{
  "name": "tunneled-connection",
  "connectionType": "mqtt",
  "uri": "tcp://mqtt.eclipseprojects.io:1883",
  "sources": [{ "..." : "..." }],
  "sshTunnel": {
    "enabled": true,
    "uri": "ssh://ssh-host:2222",
    "credentials": {
      "type": "plain",
      "username": "username",
      "password": "password"
    },
    "validateHost": true,
    "knownHosts": ["MD5:e0:3a:34:1c:68:ed:c6:bc:7c:ca:a8:67:c7:45:2b:19"]
  }
}
```

This example tunnels through `ssh-host:2222` to reach `tcp://mqtt.eclipseprojects.io:1883`.

### Password authentication

Set `credentials.type` to `plain` and provide `username` and `password` as shown above.

### Public key authentication

Set `credentials.type` to `public-key` and provide the key pair:

```json
{
  "credentials": {
    "type": "public-key",
    "username": "username",
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9.....\n-----END PUBLIC KEY-----",
    "privateKey": "-----BEGIN PRIVATE KEY-----\nMIIEvAIBADANBgkqhki....\n-----END PRIVATE KEY-----"
  }
}
```

* The public key must be PEM-encoded RSA in `X.509` format
* The private key must be PEM-encoded RSA in unencrypted `PKCS8` format per
  [RFC-7468](https://tools.ietf.org/html/rfc7468)

To convert a PKCS1 key to PKCS8:

```
openssl pkcs8 -topk8 -nocrypt -in client-private.pem.key -out client-private.pem.pk8
```

{% include note.html content="Ditto does not make any sanity check regarding the provided credentials or the
provided SSH server, e.g. if the server uses outdated ciphers or insecure keys. So make sure you configure only trusted
servers that meet your security requirements. As an additional security measure, the user associated with the given
credentials should only have assigned the least required privileges (i.e. allow only local port forwarding but no
shell access)." %}

### Host validation

{% include note.html content="It is highly recommended enabling host validation for productive systems, it should
only be disabled for testing purposes." %}

Provide fingerprints in the format produced by `ssh-keygen`. Ditto supports these hash algorithms:
`MD5`, `SHA1`, `SHA224`, `SHA256`, `SHA384`, `SHA512`.

Generate a fingerprint from a public key file:

```
ssh-keygen -lf id_rsa.pub -E md5
```

For PKCS8 format keys:

```
ssh-keygen -lf id_rsa.pub.pkcs8 -m PKCS8 -E md5
```

Example fingerprint:

```
MD5:e0:3a:34:1c:68:ed:c6:bc:7c:ca:a8:67:c7:45:2b:19
```

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [TLS certificates](connectivity-tls-certificates.html) -- secure connections with TLS
