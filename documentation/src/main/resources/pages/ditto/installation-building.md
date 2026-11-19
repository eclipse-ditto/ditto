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
arm64 hosts). Every image carries both persistence backends in `/opt/ditto/backends/{mongodb,postgresql}/`, staged by
`stage-backends.sh` from the five shaded backend extension JARs of the same build, so build those first
(`mvn clean install -DskipTests -DskipITs`). At runtime `DITTO_PERSISTENCE_BACKEND` picks one of the folders, see
[Persistence backends](installation-extending.html#persistence-backends). Before every build, `build-images.sh`
refuses a shaded backend JAR whose classes differ from the module JAR it bundles (stale after an incremental build) and
names the fix, `mvn clean install -DskipTests -DskipITs`; `CHECK_ONLY=1 ./build-images.sh` runs only that check. The
former `BAKE_POSTGRES_EXTENSIONS` variable is deprecated and has no effect.
