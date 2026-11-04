---
title: Building Ditto
tags: [getting_started, installation]
keywords: installation, docker, maven
permalink: installation-building.html
---

## Building with Apache Maven

In order to build Ditto with Maven, you'll need:
* JDK 21
* Apache Maven >=3.9 installed,
* a running Docker daemon (at least version 18.06 CE).

```bash
mvn clean install
sh build-images.sh
```

`build-images.sh` builds the snapshot images from `dockerfile-snapshot` (`DOCKERFILE=dockerfile-snapshot-arm64` for
arm64 hosts). With `BAKE_POSTGRES_EXTENSIONS=true` it also copies the PostgreSQL extension JARs of the same build
into `/opt/ditto/extensions/` of the things, policies, connectivity and things-search images, so those images can
run on PostgreSQL without mounting the JARs. Release images never contain them; see
[Extending Ditto](installation-extending.html) for mounting the JARs into a stock image.
