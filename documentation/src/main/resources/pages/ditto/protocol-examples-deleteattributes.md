---
title: Delete attributes
keywords: examples, delete attributes
search: exclude
permalink: protocol-examples-deleteattributes.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deleteattributes.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deleteattributesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/attributesdeleted.md %}{% endcapture %}
{{ event | markdownify }}

