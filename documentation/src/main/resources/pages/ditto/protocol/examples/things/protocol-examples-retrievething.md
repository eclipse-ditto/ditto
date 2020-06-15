---
title: Retrieve a Thing
keywords: examples, retrieve thing
search: exclude
permalink: protocol-examples-retrievething.html
---

{% capture command %}{% include_relative generated/commands/query/retrievething.md %}{% endcapture %}
{{ command | markdownify }}

{% capture command %}{% include_relative generated/commands/query/retrievething-withfieldselector.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/query/retrievethingresponse.md %}{% endcapture %}
{{ response | markdownify }}
