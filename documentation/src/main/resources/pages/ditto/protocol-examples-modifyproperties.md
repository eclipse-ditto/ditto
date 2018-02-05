---
title: Modify Feature Properties
keywords: examples, modify feature properties
search: exclude
permalink: protocol-examples-modifyproperties.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeatureproperties.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeaturepropertiesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featurepropertiesmodified.md %}{% endcapture %}
{{ event | markdownify }}
