---
title: Modify Feature Definition
keywords: examples, modify feature definition, definition
search: exclude
permalink: protocol-examples-modifydefinition.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeaturedefinition.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeaturedefinitionresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featuredefinitionmodified.md %}{% endcapture %}
{{ event | markdownify }}
