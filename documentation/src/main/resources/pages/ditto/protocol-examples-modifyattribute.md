---
title: Modify a single Attribute
keywords: protocol, examples, modify attribute
tags: [protocol]
search: exclude
permalink: protocol-examples-modifyattribute.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyattribute.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyattributeresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/attributemodified.md %}{% endcapture %}
{{ event | markdownify }}

