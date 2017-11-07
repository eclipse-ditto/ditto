---
title: Delete a Thing
keywords: examples, delete thing
search: exclude
permalink: protocol-examples-deletething.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/deletething.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/deletethingresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/thingdeleted.md %}{% endcapture %}
{{ event | markdownify }}

