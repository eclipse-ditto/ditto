---
title: Modify a Thing
keywords: examples, modify thing
search: exclude
permalink: protocol-examples-modifything.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/modifything.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/modifythingresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/thingmodified.md %}{% endcapture %}
{{ event | markdownify }}

