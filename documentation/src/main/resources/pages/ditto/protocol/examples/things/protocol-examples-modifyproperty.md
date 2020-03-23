---
title: Modify a single property
keywords: examples, modify property
search: exclude
permalink: protocol-examples-modifyproperty.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeatureproperty.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeaturepropertyresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featurepropertymodified.md %}{% endcapture %}
{{ event | markdownify }}
