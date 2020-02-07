---
title: Delete a definition
keywords: examples, definition
search: exclude
permalink: protocol-examples-deletethingdefinition.html
---

{% capture command %}{% include_relative generated/commands/modify/deletethingdefinition.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/deletethingdefinitionresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/thingdefinitiondeleted.md %}{% endcapture %}
{{ event | markdownify }}

