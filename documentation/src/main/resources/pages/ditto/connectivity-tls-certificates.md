---
title: TLS Certificates
keywords: security, TLS
tags: [connectivity]
permalink: connectivity-tls-certificates.html
---

[awsiot]: https://docs.aws.amazon.com/iot/
[der]: https://en.wikipedia.org/wiki/X.690#DER_encoding
[ocsp]: https://tools.ietf.org/html/rfc6960
[openssl]: https://www.openssl.org/
[pem]: https://en.wikipedia.org/wiki/Privacy-Enhanced_Mail
[pkcs8]: https://tools.ietf.org/html/rfc5208
[rfc5280]: https://tools.ietf.org/html/rfc5280
[rfc7468]: https://tools.ietf.org/html/rfc7468

You use TLS certificates to secure connections between Ditto and external message brokers, verifying server identity and optionally authenticating Ditto as a client.

{% include callout.html content="**TL;DR**: Set `validateCertificates: true` and provide a `ca` certificate to verify server identity. Add `credentials` with type `client-cert` to authenticate Ditto with a client certificate." type="primary" %}

## Overview

Ditto supports two certificate-based security features:

1. **Server certificate verification** -- confirms the identity of the external endpoint
2. **Client certificate authentication** -- authenticates Ditto at the external endpoint

Both features are available for
[AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html),
[AMQP 1.0](connectivity-protocol-bindings-amqp10.html),
[MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html),
[MQTT 5](connectivity-protocol-bindings-mqtt5.html),
[HTTP 1.1](connectivity-protocol-bindings-http.html), and
[Kafka 2.x](connectivity-protocol-bindings-kafka2.html) connections.

## How it works

### Server certificate verification

Verifying the server identity mitigates man-in-the-middle attacks. To enable it, use a secure
transport protocol and set `validateCertificates` to `true`:

```json
{
  "uri": "<secure-protocol>://<host>:<port>/<path>",
  "validateCertificates": true,
  "ca": "-----BEGIN CERTIFICATE-----\n<trusted certificate>\n-----END CERTIFICATE-----"
}
```

| Field | Description |
|-------|-------------|
| `uri` | Use a secure protocol such as `amqps`, `ssl`, or `https` |
| `validateCertificates` | Must be `true` |
| `ca` | Trusted certificates as [PEM][pem]-encoded [DER][der]. Concatenate multiple certificates to trust all of them. Omit to trust public CAs. |

Ditto verifies the server identity in one of three ways:

**Direct verification** -- the exact server certificate is in the `ca` field. TLS proves the
server possesses the matching private key.

**Indirect verification via trusted party** -- the server certificate is signed by a CA whose
certificate is in the `ca` field, and the connection URI hostname matches the server certificate
(as CN or SAN). IPv4/IPv6 addresses must be listed as a SAN of type IP per [RFC-5280][rfc5280].
Revocation of individual certificates is not supported.

**Indirect verification via public CAs** -- the `ca` field is omitted, and the server certificate
is signed by a generally accepted CA. Ditto attempts to exclude revoked certificates via [OCSP][ocsp].

{% include note.html content="Certificates signed by public CAs get compromised on a daily basis.
   It is more secure to upload your message broker's certificate directly even if it was signed
   by a public CA. Then the Ditto connection trusts only your broker (or rather any holder of the broker's private key).
   To minimize unavailability due to certificate expiry, upload both: the current broker certificate and the next
   certificate as a concatenated string." %}

### Client certificate authentication

_Client-certificate authentication is available for
[MQTT](connectivity-protocol-bindings-mqtt.html),
[HTTP](connectivity-protocol-bindings-http.html),
[AMQP 1.0](connectivity-protocol-bindings-amqp10.html), and
[Kafka 2.x](connectivity-protocol-bindings-kafka2.html) connections._

Configure a client certificate to authenticate Ditto at your message broker:

```json
{
  "uri": "<secure-protocol>://<host>:<port>/<path>",
  "credentials": {
    "type": "client-cert",
    "cert": "-----BEGIN CERTIFICATE-----\n<client certificate>\n-----END CERTIFICATE-----",
    "key": "-----BEGIN PRIVATE KEY-----\n<client private key>\n-----END PRIVATE KEY-----"
  }
}
```

| Field | Description |
|-------|-------------|
| `credentials/type` | Must be `client-cert` |
| `credentials/cert` | Client certificate as [PEM][pem]-encoded [DER][der] |
| `credentials/key` | Client private key as PEM-encoded [PKCS8][pkcs8] per [RFC-7468][rfc7468]. The PEM preamble must be `-----BEGIN PRIVATE KEY-----`. |

### Converting PKCS1 keys to PKCS8

[OpenSSL][openssl] and [AWS IoT][awsiot] generate PKCS1 keys by default
(`-----BEGIN RSA PRIVATE KEY-----`), which Ditto rejects. Convert with:

```
openssl pkcs8 -topk8 -nocrypt -in client-private.pem.key -out client-private.pem.pk8
```

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [SSH tunneling](connectivity-ssh-tunneling.html) -- tunnel connections through SSH
* [HMAC signing](connectivity-hmac-signing.html) -- HMAC-based authentication
