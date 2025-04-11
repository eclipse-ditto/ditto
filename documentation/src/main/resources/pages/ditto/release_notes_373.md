---
title: Release notes 3.7.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.3 of Eclipse Ditto, released on 11.04.2025"
permalink: release_notes_373.html
---

This is a bugfix release, no new features since [3.7.2](release_notes_372.html) were added.

## Changelog

Compared to the latest release [3.7.2](release_notes_372.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.7.3).

#### Aggregation metrics support `exists(/path/to/field)` RQL operator

PR [#2155](https://github.com/eclipse-ditto/ditto/pull/2155) fixes that aggregation metrics didn't support the `exists()` 
RQL operator.

{% include warning.html content="**Change in config is necessary when upgrading to 3.7.3!**<br>
If you configured `custom-aggregation-metrics` (added in Ditto 3.7), you need to adapt your configuration.<br>
- No longer, multiple filters will be an option for single metric. The filters object is removed and now a single filter property is used.<br>
- Respectfully, the inline placeholders are no longer available as they made sense only in the context of multiple filters." %}

We apologize for the breaking change, but it was necessary to adjust in order to 
fix bug [#2154](https://github.com/eclipse-ditto/ditto/issues/2154).

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
            "health" = "{%raw%}{{ inline:health }}{%endraw%}"
        }
        
    }
}
```

becomes (has to be migrated to):
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

#### Fix WoT based validation rejecting message API calls if content-type was not application/json

When the configuration `log-warning-instead-of-failing-api-calls` was enabled to not fail API calls after failed WoT model
validation, but only log a warning message, the API calls were still rejected if the content type was not `application/json`.  
This was fixed in PR [#2157](https://github.com/eclipse-ditto/ditto/pull/2157).

#### Stabilize pre-defined extraFields enrichment, treating occurring exceptions by not pre-enriching

PR [#2158](https://github.com/eclipse-ditto/ditto/pull/2158) stabilizes the pre-defined extraFields enrichment which
was added in Ditto 3.7. It could happen that - even if the pre-defined extraFields enrichment was **not** configured - the
mechanism was still active and caused trouble (timeouts of messages in combination with acknowledgements were observed by a Ditto adopter).  
The stabilization fixes this issue.
