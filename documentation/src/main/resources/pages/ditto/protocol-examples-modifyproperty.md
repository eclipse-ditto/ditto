---
title: Modify a single property
keywords: examples, modify property
search: exclude
permalink: protocol-examples-modifyproperty.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeatureproperty.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeaturepropertyresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featurepropertymodified.md %}{% endcapture %}
{{ event | markdownify }}
