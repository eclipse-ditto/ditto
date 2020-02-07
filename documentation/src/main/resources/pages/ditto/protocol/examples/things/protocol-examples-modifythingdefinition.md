---
title: Modify a definition
keywords: examples, definition
search: exclude
permalink: protocol-examples-modifythingdefinition.html
---

{% capture command %}{% include_relative generated/commands/modify/modifythingdefinition.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifythingdefinitionresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/thingdefinitionmodified.md %}{% endcapture %}
{{ event | markdownify }}

