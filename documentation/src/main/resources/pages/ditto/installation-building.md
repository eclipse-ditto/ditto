---
title: Building Ditto
tags: [getting_started, installation]
keywords: installation, docker, maven
permalink: installation-building.html
---

## Building with Apache Maven

In order to build Ditto with Maven, you'll need:
* JDK 17 >= 17.0.2,
* Apache Maven >=3.8 installed,
* a running Docker daemon (at least version 18.06 CE).

```bash
mvn clean install
sh build-images.sh
```
