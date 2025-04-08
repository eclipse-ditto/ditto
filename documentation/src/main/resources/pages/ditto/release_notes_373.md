---
title: Release notes 3.7.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.3 of Eclipse Ditto, released on 31.03.2025"
permalink: release_notes_373.html
---

This is a bugfix release, no new features since [3.7.2](release_notes_372.html) were added.

## Changelog

Compared to the latest release [3.7.2](release_notes_372.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.7.2).

#### Aggregation metrics support `exists(/path/to/field)` rql operator.

PR [#2155](https://github.com/eclipse-ditto/ditto/pull/2155) fixes that  aggregation metrics didn't support exists()
rql operator.

> :note: **Change in config is necessary when upgrading to 3.7.3!.**
> - No longer, multiple filters will be an option for single metric. The filters object is removed and now a single filter property is used.
> - Respectfully, the inline placeholders are no longer available as they made sense only in the context of multiple filters.

```hocon

custom-aggregation-metrics {
    online_status {
        namespaces = []
        filters {
            online_filter {
                filter = "gt(features/ConnectionStatus/properties/status/readyUntil,time:now)"
                inline-placeholder-values  {
                    "health" = "good"
                }
            }
            offline_filter {
                filter = "lt(features/ConnectionStatus/properties/status/readyUntil,time:now)"
                inline-placeholder-values = {
                    "health" = "bad"
                }
            }
        }
        group-by {...}
        tags {
            "health" = "{{ inline:health }}"
        }
        
    }
}

```
becomes
```hocon
custom-aggregation-metrics {
    online {
        namespaces = []
        filter = "gt(features/ConnectionStatus/properties/status/readyUntil/,time:now)"
        group-by {}
        tags {
            "health" = "good"
        }
    }
    offline {
        namespaces = []
        filter = "lt(features/ConnectionStatus/properties/status/readyUntil/,time:now)"
        group-by {}
        tags {
            "health" = "bad"
        }
    }
}
``` 