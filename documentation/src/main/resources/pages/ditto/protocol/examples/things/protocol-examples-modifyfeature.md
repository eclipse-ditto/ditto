---
title: Modify a single feature
keywords: examples, modify feature
search: exclude
permalink: protocol-examples-modifyfeature.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeature.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeatureresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featuremodified.md %}{% endcapture %}
{{ event | markdownify }}
