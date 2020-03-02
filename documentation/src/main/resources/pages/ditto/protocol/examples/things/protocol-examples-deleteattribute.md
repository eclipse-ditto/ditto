---
title: Delete a single attribute
keywords: examples, delete attribute
search: exclude
permalink: protocol-examples-deleteattribute.html
---

{% capture command %}{% include_relative generated/commands/modify/deleteattribute.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/deleteattributeresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/attributedeleted.md %}{% endcapture %}
{{ event | markdownify }}

