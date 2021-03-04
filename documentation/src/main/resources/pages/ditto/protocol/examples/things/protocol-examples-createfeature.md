---
title: Create a single feature
keywords: examples, create feature
search: exclude
permalink: protocol-examples-createfeature.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeature.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeatureresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featurecreated.md %}{% endcapture %}
{{ event | markdownify }}
