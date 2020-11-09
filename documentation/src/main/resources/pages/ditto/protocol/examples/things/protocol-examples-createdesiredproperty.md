---
title: Create a single desired property
keywords: examples, create desiredProperty
search: exclude
permalink: protocol-examples-createdesiredproperty.html
---

{% capture command %}{% include_relative generated/commands/modify/modifyfeaturedesiredproperty.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifyfeaturedesiredpropertyresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/featuredesiredpropertycreated.md %}{% endcapture %}
{{ event | markdownify }}