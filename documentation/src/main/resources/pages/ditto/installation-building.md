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
./build-images.sh
```

`build-images.sh` builds the snapshot images from `dockerfile-snapshot` (`DOCKERFILE=dockerfile-snapshot-arm64` for
arm64 hosts). With `BAKE_POSTGRES_EXTENSIONS=true` it also copies the PostgreSQL extension JARs of the same build
into `/opt/ditto/extensions/` of the things, policies, connectivity and things-search images, so those images can
run on PostgreSQL without mounting the JARs. It refuses to bake a shaded extension JAR whose classes differ from the
module JAR it bundles (stale after an incremental build) and names the fix, `mvn clean install -DskipTests -DskipITs`;
`BAKE_POSTGRES_EXTENSIONS=true CHECK_ONLY=1 ./build-images.sh` runs only that check. Release images never contain the
JARs; see [Running Ditto on PostgreSQL](installation-postgresql.html) for mounting them into a stock image.
