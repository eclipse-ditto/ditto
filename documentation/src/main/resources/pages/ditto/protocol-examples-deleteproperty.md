---
title: Delete a single Property
keywords: protocol, examples, delete property
tags: [protocol]
search: exclude
permalink: protocol-examples-deleteproperty.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deletefeatureproperty.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deletefeaturepropertyresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featurepropertydeleted.md %}{% endcapture %}
{{ event | markdownify }}
