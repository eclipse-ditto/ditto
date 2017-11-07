---
title: Create a single Property
keywords: protocol, examples, create property
tags: [protocol]
search: exclude
permalink: protocol-examples-createproperty.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeatureproperty.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeaturepropertyresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featurepropertycreated.md %}{% endcapture %}
{{ event | markdownify }}
