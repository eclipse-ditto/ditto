---
title: Modify a definition
keywords: examples, definition
search: exclude
permalink: protocol-examples-modifythingdefinition.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifythingdefinition.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifythingdefinitionresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/thingdefinitionmodified.md %}{% endcapture %}
{{ event | markdownify }}

