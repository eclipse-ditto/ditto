---
title: Create a single feature
keywords: examples, create feature
search: exclude
permalink: protocol-examples-deletefeature.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deletefeature.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deletefeatureresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featuredeleted.md %}{% endcapture %}
{{ event | markdownify }}
