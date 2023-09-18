---
title: Release notes 3.4.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.4.0 of Eclipse Ditto, released on 13.09.2023"
permalink: release_notes_340.html
---

The fourth minor release of Ditto 3.x, Eclipse Ditto version 3.4.0 is here.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto 3.4.0 focuses on the following areas:

* Swapping the Akka framework (because of its switch of license to [BSL License](https://www.lightbend.com/akka/license-faq) after Akka v2.6.x)
  with its fork [Apache Pekko](https://pekko.apache.org/docs/pekko/current/index.html) which is Apache 2.0 licensed. [#1477](https://github.com/eclipse-ditto/ditto/issues/1477)
* Improve logging by adding the W3C traceparent header as MDC field to logs [#1739](https://github.com/eclipse-ditto/ditto/issues/1739)
* Optimize docker files [#1744](https://github.com/eclipse-ditto/ditto/issues/1744)

### Migrating to Ditto 3.4.x

To migrate a running system with live data there are few configurations that should be overridden with Java system properties in the following services.

Policies:
```markdown
* -Dpekko-contrib-mongodb-persistence-policies-journal.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-policies-journal-read.overrides.metadata-index=akka_persistence_metadata_pid
```

Things:
```markdown
* -Dpekko-contrib-mongodb-persistence-things-journal.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-things-journal-read.overrides.metadata-index=akka_persistence_metadata_pid
```

Connectivity:
```markdown
* -Dpekko-contrib-mongodb-persistence-connection-journal.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-connection-journal-read.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-connection-remember-journal.overrides.metadata-index=akka_persistence_metadata_pid
```

Full cluster recreate is required, rolling update is not supported as there are changes in the management
urls and ports.

Other than that the transition should be smooth.

