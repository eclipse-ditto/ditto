---
title: Delete features
keywords: examples, delete features
search: exclude
permalink: protocol-examples-deletefeatures.html
---

{% capture command %}{% include_relative generated/commands/modify/deletefeatures.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/deletefeaturesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featuresdeleted.md %}{% endcapture %}
{{ event | markdownify }}
