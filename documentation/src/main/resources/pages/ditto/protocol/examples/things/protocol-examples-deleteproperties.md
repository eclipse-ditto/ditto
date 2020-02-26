---
title: Delete Feature Properties
keywords: examples, delete feature properties
search: exclude
permalink: protocol-examples-deleteproperties.html
---

{% capture command %}{% include_relative generated/commands/modify/deletefeatureproperties.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/deletefeaturepropertiesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featurepropertiesdeleted.md %}{% endcapture %}
{{ event | markdownify }}
