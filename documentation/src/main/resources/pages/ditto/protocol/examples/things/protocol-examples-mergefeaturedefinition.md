---
title: Merge the definition of a feature 
keywords: examples, merge feature definition 
search: exclude 
permalink: protocol-examples-mergefeaturedefinition.html
---

{% capture command %}{% include_relative generated/commands/merge/mergefeaturedefinition.md %}{% endcapture %} {{
command | markdownify }}

{% capture response %}{% include_relative generated/commands/merge/mergefeaturedefinitionresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/mergedfeaturedefinition.md %}{% endcapture %} {{ event |
markdownify }}

{% capture command %}{% include_relative generated/commands/merge/mergedeletefeaturedefinition.md %}{% endcapture %} {{
command | markdownify }}

