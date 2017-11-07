---
title: Delete Properties
keywords: protocol, examples, delete properties
tags: [protocol]
search: exclude
permalink: protocol-examples-deleteproperties.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deletefeatureproperties.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deletefeaturepropertiesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featurepropertiesdeleted.md %}{% endcapture %}
{{ event | markdownify }}
