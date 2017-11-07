---
title: Retrieve a Thing
keywords: protocol, examples, retrieve thing
tags: [protocol]
search: exclude
permalink: protocol-examples-retrievething.html
---

{% capture command %}{% include_relative protocol/things/commands/query/retrievething.md %}{% endcapture %}
{{ command | markdownify }}

{% capture command %}{% include_relative protocol/things/commands/query/retrievething-withfieldselector.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/query/retrievethingresponse.md %}{% endcapture %}
{{ response | markdownify }}
