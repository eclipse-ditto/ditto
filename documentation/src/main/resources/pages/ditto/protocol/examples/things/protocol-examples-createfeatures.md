---
title: Create features
keywords: examples, create features
search: exclude
permalink: protocol-examples-createfeatures.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeatures.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeaturesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featurescreated.md %}{% endcapture %}
{{ event | markdownify }}
