---
title: Modify attributes
keywords: examples, modify attributes
search: exclude
permalink: protocol-examples-modifyattributes.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyattributes.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyattributesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/attributesmodified.md %}{% endcapture %}
{{ event | markdownify }}

