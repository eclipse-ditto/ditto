---
title: Create Feature Properties
keywords: examples, create feature properties
search: exclude
permalink: protocol-examples-createproperties.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifyfeatureproperties.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifyfeaturepropertiesresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/featurepropertiescreated.md %}{% endcapture %}
{{ event | markdownify }}
