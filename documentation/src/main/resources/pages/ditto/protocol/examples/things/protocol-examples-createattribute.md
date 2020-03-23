---
title: Create a single attribute
keywords: examples, create attribute
search: exclude
permalink: protocol-examples-createattribute.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyattribute.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyattributeresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/attributecreated.md %}{% endcapture %}
{{ event | markdownify }}

