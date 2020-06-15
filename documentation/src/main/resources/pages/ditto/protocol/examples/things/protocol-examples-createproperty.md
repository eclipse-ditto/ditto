---
title: Create a single property
keywords: examples, create property
search: exclude
permalink: protocol-examples-createproperty.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeatureproperty.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeaturepropertyresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featurepropertycreated.md %}{% endcapture %}
{{ event | markdownify }}
