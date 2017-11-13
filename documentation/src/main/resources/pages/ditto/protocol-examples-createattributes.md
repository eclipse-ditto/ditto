---
title: Create attributes
keywords: examples, create attributes
search: exclude
permalink: protocol-examples-createattributes.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyattributes.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyattributesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/attributescreated.md %}{% endcapture %}
{{ event | markdownify }}
