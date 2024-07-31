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
