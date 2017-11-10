---
title: Create features
keywords: examples, create features
search: exclude
permalink: protocol-examples-createfeatures.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeatures.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeaturesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featurescreated.md %}{% endcapture %}
{{ event | markdownify }}
