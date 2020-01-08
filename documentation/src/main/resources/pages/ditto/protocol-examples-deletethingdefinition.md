---
title: Delete a definition
keywords: examples, definition
search: exclude
permalink: protocol-examples-deletethingdefinition.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deletethingdefinition.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deletethingdefinitionresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/thingdefinitiondeleted.md %}{% endcapture %}
{{ event | markdownify }}

