---
title: Create a single Features
keywords: protocol, examples, create feature
tags: [protocol]
search: exclude
permalink: protocol-examples-createfeature.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeature.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeatureresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featuremodified.md %}{% endcapture %}
{{ event | markdownify }}
