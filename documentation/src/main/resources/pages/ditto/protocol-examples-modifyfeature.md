---
title: Create a single feature
keywords: examples, create feature
search: exclude
permalink: protocol-examples-modifyfeature.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeature.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeatureresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featuremodified.md %}{% endcapture %}
{{ event | markdownify }}
