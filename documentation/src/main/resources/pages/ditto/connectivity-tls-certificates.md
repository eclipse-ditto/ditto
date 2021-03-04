---
title: Certificates for Transport Layer Security
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

## Verify server certificate

_Server-certificate verification is available for
[AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html),
[AMQP 1.0](connectivity-protocol-bindings-amqp10.html),
[MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html),
[MQTT 5](connectivity-protocol-bindings-mqtt5.html),
[HTTP 1.1](connectivity-protocol-bindings-http.html), and
[Kafka 2.x](connectivity-protocol-bindings-kafka2.html) connections._

### Connection configuration

Verifying the server identity mitigates the risk of man-in-the-middle attacks.
To have Ditto check the identity of external message brokers,
choose a secure transport protocol and set the flag `validateCertificates` to `true` in your
[connections](basic-connections.html).

```json
{
  "uri": "<secure-transport-protocol>://<host>:<port>/<path>",
  "validateCertificates": true,
  "ca": "-----BEGIN CERTIFICATE-----\n<trusted certificate>\n-----END CERTIFICATE-----"
}
```

- `uri`: Choose a secure transport protocol such as `amqps` or `ssl`.
- `validateCertificates`: Must be `true`.
- `ca`: A string of trusted certificates as [PEM][pem]-encoded [DER][der]. Concatenate multiple certificates as
        strings to trust all of them. Omit to trust popular certificate authorities.

### Server identity check

When Ditto opens a secure connection, the external message broker provides a server certificate.
The server identity is verified directly or indirectly.

- **Direct identity verification**:
  The exact server certificate is listed as a trusted certificate in the connection configuration `ca`.
  Establishing a TLS session proves that the external message broker possesses the private key matching the server
  certificate.

- **Indirect identity verification via a trusted party**:
  The server certificate is signed by a trusted party, whose certificate is in the connection configuration `ca`, and
  the host-component of the connection URI is listed in the server certificate.

  If the host-component is a DNS name, then it should be listed as the common name (CN)
  or a subject alternative name (SAN) of type DNS.

  If the host-component is an IPv4 or IPv6 address, then it should be listed as a subject alternative name (SAN)
  of type IP. Any IP addresses in the common name (CN) are ignored per [RFC-5280][rfc5280].

  Revocation of individual certificates is *not supported*. For each certificate in the `ca` field, you extend trust to 
  _all_ certificates signed by it.

- **Indirect identity verification via public certificate authorities**:
  The server certificate is signed by a generally accepted certificate authority, the host-component
  of the connection URI is listed in the server certificate, and the `ca` field is omitted in the connection 
  configuration.

  Ditto will try its best to exclude revoked certificates via [OCSP][ocsp].

{% include note.html content="Certificates signed by public CAs get compromised on a daily basis.
   It is more secure to upload your message broker's certificate directly even if it was signed
   by a public CA. Then the Ditto connection trusts only your broker (or rather any holder of the broker's private key).
   To minimize unavailability due to certificate expiry, upload both: the current broker certificate and the next
   certificate as a concatenated string." %}

## Authenticate by client certificate

_Client-certificate authentication is available for [MQTT connections](connectivity-protocol-bindings-mqtt.html)
 and [HTTP connections](connectivity-protocol-bindings-http.html) only._

### Connection configuration

Configure a client certificate for Ditto in the `credentials` field of the connection configuration to authenticate
Ditto at your message broker.

```json
{
  "uri": "<secure-transport-protocol>://<host>:<port>/<path>",
  "credentials": {
    "type": "client-cert",
    "cert": "-----BEGIN CERTIFICATE-----\n<client certificate>\n-----END CERTIFICATE-----",
    "key": "-----BEGIN PRIVATE KEY-----\n<client private key>\n-----END PRIVATE KEY-----"
  }
}
```

- `uri`: Choose a secure transport protocol such as `ssl`.

- `credentials/type`: Must be `client-cert`.

- `credentials/cert`: The client certificate as [PEM][pem]-encoded [DER][der].

- `credentials/key`: The client private key for Ditto as PEM-encoded [PKCS8][pkcs8] specified by [RFC-7468][rfc7468];
the PEM preamble must be `-----BEGIN PRIVATE KEY-----`.

As of September 2018, [OpenSSL][openssl] and [AWS IoT][awsiot] generate PKCS1-coded private keys by default, which
have the PEM preamble `-----BEGIN RSA PRIVATE KEY-----`. Ditto will reject these keys. The command below converts a
PKCS1 key file `client-private.pem.key` into a PKCS8 key file `client-private.pem.pk8` accepted by Ditto.

```
openssl pkcs8 -topk8 -nocrypt -in client-private.pem.key -out client-private.pem.pk8
```
