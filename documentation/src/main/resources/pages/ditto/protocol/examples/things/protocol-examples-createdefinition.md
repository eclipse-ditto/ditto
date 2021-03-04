---
title: Create Feature Definition
keywords: examples, create feature definition, definition
search: exclude
permalink: protocol-examples-createdefinition.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeaturedefinition.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeaturedefinitionresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featuredefinitioncreated.md %}{% endcapture %}
{{ event | markdownify }}
