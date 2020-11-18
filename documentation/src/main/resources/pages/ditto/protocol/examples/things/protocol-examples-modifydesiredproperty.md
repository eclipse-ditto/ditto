---
title: Modify a single desired property
keywords: examples, modify desiredProperty
search: exclude
permalink: protocol-examples-modifydesiredproperty.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeaturedesiredproperty.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeaturedesiredpropertyresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featuredesiredpropertymodified.md %}{% endcapture %}
{{ event | markdownify }}