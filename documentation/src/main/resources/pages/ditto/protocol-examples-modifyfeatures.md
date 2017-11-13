---
title: Modify features
keywords: examples, modify features
search: exclude
permalink: protocol-examples-modifyfeatures.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeatures.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeaturesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featuresmodified.md %}{% endcapture %}
{{ event | markdownify }}
