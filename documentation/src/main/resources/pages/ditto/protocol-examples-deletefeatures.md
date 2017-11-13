---
title: Delete features
keywords: examples, delete features
search: exclude
permalink: protocol-examples-deletefeatures.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deletefeatures.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deletefeaturesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featuresdeleted.md %}{% endcapture %}
{{ event | markdownify }}
