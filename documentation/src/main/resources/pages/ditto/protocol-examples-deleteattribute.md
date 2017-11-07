---
title: Delete a single Attribute
keywords: protocol, examples, delete attribute
tags: [protocol]
search: exclude
permalink: protocol-examples-deleteattribute.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deleteattribute.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deleteattributeresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/attributedeleted.md %}{% endcapture %}
{{ event | markdownify }}

