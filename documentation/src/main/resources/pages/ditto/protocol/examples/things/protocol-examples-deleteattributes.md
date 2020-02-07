---
title: Delete attributes
keywords: examples, delete attributes
search: exclude
permalink: protocol-examples-deleteattributes.html
---

{% capture command %}{% include_relative generated/commands/modify/deleteattributes.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/deleteattributesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/attributesdeleted.md %}{% endcapture %}
{{ event | markdownify }}

