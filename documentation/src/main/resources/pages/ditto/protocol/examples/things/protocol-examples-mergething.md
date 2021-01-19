---
title: Merge a Thing 
keywords: examples, merge thing 
search: exclude 
permalink: protocol-examples-mergething.html
---

{% capture command %}{% include_relative generated/commands/merge/mergething.md %}{% endcapture %} {{ command |
markdownify }}

{% capture response %}{% include_relative generated/commands/merge/mergethingresponse.md %}{% endcapture %} {{ response
| markdownify }}

{% capture event %}{% include_relative generated/events/mergedthing.md %}{% endcapture %} {{ event | markdownify }}
