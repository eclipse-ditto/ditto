---
title: Create a definition
keywords: examples, definition
search: exclude
permalink: protocol-examples-createthingdefinition.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifythingdefinition.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifythingdefinitionresponsecreated.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/thingdefinitioncreated.md %}{% endcapture %}
{{ event | markdownify }}