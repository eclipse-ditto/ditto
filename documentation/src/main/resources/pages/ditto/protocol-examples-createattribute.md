---
title: Create a single attribute
keywords: examples, create attribute
search: exclude
permalink: protocol-examples-createattribute.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyattribute.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyattributeresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/attributecreated.md %}{% endcapture %}
{{ event | markdownify }}

