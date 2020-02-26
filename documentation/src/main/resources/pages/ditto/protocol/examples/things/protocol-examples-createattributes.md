---
title: Create attributes
keywords: examples, create attributes
search: exclude
permalink: protocol-examples-createattributes.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyattributes.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyattributesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/attributescreated.md %}{% endcapture %}
{{ event | markdownify }}
