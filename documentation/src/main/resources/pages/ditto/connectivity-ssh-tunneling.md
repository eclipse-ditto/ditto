---
title: SSH tunneling
keywords: ssh, tunnel, tunneling, port forwarding
tags: [connectivity]
permalink: connectivity-ssh-tunneling.html
---

## SSH tunneling

A managed connection supports establishing an SSH tunnel 
(see section TCP/IP Port Forwarding of the 
[Secure Shell (SSH) Connection Protocol, RFC4254](https://tools.ietf.org/html/rfc4254#section-7)) which 
is then used to connect to the actual target endpoint. 
This is useful when the target endpoint is not directly accessible but only via SSH. For this purpose the connection 
configuration must specify the `sshTunnel` section, which contains the necessary 
information to establish a local SSH port forwarding. The tunneling supports password and public key authentication and 
host validation using public key fingerprints. If the tunnel is enabled the connection will establish an SSH 
tunnel and afterwards use this tunnel to connect to the actual endpoint.

The example below establishes an SSH tunnel via `ssh-host:2222` to the remote endpoint 
`tcp://mqtt.eclipseprojects.io:1883`, using plain authentication and enabled host validation:

```json
{
    "name": "tunneled-connection",
    "connectionType": "mqtt",
    "uri": "tcp://mqtt.eclipseprojects.io:1883",
    "sources": [{ ... }],
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

{% include note.html content="When using SSH tunneling, keep in mind that it can have an impact on the transmission 
performance of your connection compared to transmission performance of a direct connection." %}

### Public key authentication

An SSH tunnel can also be authenticated using public key authentication. The credentials provided in the SSH tunnel 
configuration must then be of the type `public-key`:
```json
...
"credentials": {
    "type": "public-key",
    "username": "username",
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9.....\n-----END PUBLIC KEY-----",
    "privateKey": "-----BEGIN PRIVATE KEY-----\nMIIEvAIBADANBgkqhki....\n-----END PRIVATE KEY-----"
}
...
```

The public key must be provided as PEM-encoded RSA key in `X.509` format.
The private key must be provided as PEM-encoded RSA key in unencrypted `PKCS8` format as specified by 
[RFC-7468](https://tools.ietf.org/html/rfc7468).

The following command can be used to convert a standard OpenSSL key in PKCS1 format to the PKCS8 format accepted by 
Ditto:
```
openssl pkcs8 -topk8 -nocrypt -in client-private.pem.key -out client-private.pem.pk8
```

{% include note.html content="Ditto does not make any sanity check regarding the provided credentials or the 
provided SSH server, e.g. if the server uses outdated ciphers or insecure keys. So make sure you configure only trusted 
servers that meet your security requirements. As an additional security measure, the user associated with the given 
credentials should only have assigned the least required privileges (i.e. allow only local port forwarding but no 
shell access)." %}

### SSH host validation

{% include note.html content="It is highly recommended enabling host validation for productive systems, it should 
only be disabled for testing purposes." %}

The accepted fingerprints can be provided in the format the standard command line tool `ssh-keygen` produces. 

Example:
```
MD5:e0:3a:34:1c:68:ed:c6:bc:7c:ca:a8:67:c7:45:2b:19
```
The fingerprints are prefixed with an alias of the hash algorithm that was used to calculate the fingerprint. Ditto 
supports the following hash algorithms for public key fingerprints:  `MD5`, `SHA1`, `SHA224`, `SHA256`, `SHA384` and `SHA512`. 

Assuming the file `id_rsa.pub` contains the public key the following command produces a valid fingerprint that 
can be used in the SSH tunnel configuration:
```
ssh-keygen -lf id_rsa.pub -E md5
```
Or in case the public key is given in PKCS8 format:
```
ssh-keygen -lf id_rsa.pub.pkcs8 -m PKCS8 -E md5
```
