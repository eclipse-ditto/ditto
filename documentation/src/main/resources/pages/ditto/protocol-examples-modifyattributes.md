---
title: Modify attributes
keywords: examples, modify attributes
search: exclude
permalink: protocol-examples-modifyattributes.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyattributes.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyattributesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/attributesmodified.md %}{% endcapture %}
{{ event | markdownify }}

