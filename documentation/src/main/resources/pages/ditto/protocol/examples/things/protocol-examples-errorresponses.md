---
title: Things error responses
keywords: examples, things error responses, error responses
search: exclude
permalink: protocol-examples-errorresponses.html
---

The following listed example errors do not claim to be a complete list of all available and possible error responses.  
Also, their texts and **error** codes might be outdated.

In order to find out all currently available errors, please inspect Ditto's codebase and look at all implementations of
the `ThingException` interface.

{% capture things_attribute_notmodifiable %}{% include_relative generated/exceptions/things_attribute_notmodifiable.md %}{% endcapture %}
{{ things_attribute_notmodifiable | markdownify }}

{% capture things_attributes_notfound %}{% include_relative generated/exceptions/things_attributes_notfound.md %}{% endcapture %}
{{ things_attributes_notfound | markdownify }}

{% capture things_attributes_notmodifiable %}{% include_relative generated/exceptions/things_attributes_notmodifiable.md %}{% endcapture %}
{{ things_attributes_notmodifiable | markdownify }}

{% capture things_feature_notfound %}{% include_relative generated/exceptions/things_feature_notfound.md %}{% endcapture %}
{{ things_feature_notfound | markdownify }}

{% capture things_feature_notmodifiable %}{% include_relative generated/exceptions/things_feature_notmodifiable.md %}{% endcapture %}
{{ things_feature_notmodifiable | markdownify }}

{% capture things_feature_properties_notfound %}{% include_relative generated/exceptions/things_feature_properties_notfound.md %}{% endcapture %}
{{ things_feature_properties_notfound | markdownify }}

{% capture things_feature_properties_notmodifiable %}{% include_relative generated/exceptions/things_feature_properties_notmodifiable.md %}{% endcapture %}
{{ things_feature_properties_notmodifiable | markdownify }}

{% capture things_feature_property_notfound %}{% include_relative generated/exceptions/things_feature_property_notfound.md %}{% endcapture %}
{{ things_feature_property_notfound | markdownify }}

{% capture things_feature_property_notmodifiable %}{% include_relative generated/exceptions/things_feature_property_notmodifiable.md %}{% endcapture %}
{{ things_feature_property_notmodifiable | markdownify }}

{% capture things_feature_desired_properties_notfound %}{% include_relative generated/exceptions/things_feature_desired_properties_notfound.md %}{% endcapture %}
{{ things_feature_desired_properties_notfound | markdownify }}

{% capture things_feature_desired_properties_notmodifiable %}{% include_relative generated/exceptions/things_feature_desired_properties_notmodifiable.md %}{% endcapture %}
{{ things_feature_desired_properties_notmodifiable | markdownify }}

{% capture things_feature_desired_property_notfound %}{% include_relative generated/exceptions/things_feature_desired_property_notfound.md %}{% endcapture %}
{{ things_feature_desired_property_notfound | markdownify }}

{% capture things_feature_desired_property_notmodifiable %}{% include_relative generated/exceptions/things_feature_desired_property_notmodifiable.md %}{% endcapture %}
{{ things_feature_desired_property_notmodifiable | markdownify }}

{% capture things_features_notfound %}{% include_relative generated/exceptions/things_features_notfound.md %}{% endcapture %}
{{ things_features_notfound | markdownify }}

{% capture things_features_notmodifiable %}{% include_relative generated/exceptions/things_features_notmodifiable.md %}{% endcapture %}
{{ things_features_notmodifiable | markdownify }}

{% capture things_id_invalid %}{% include_relative generated/exceptions/things_id_invalid.md %}{% endcapture %}
{{ things_id_invalid | markdownify }}

{% capture things_id_notsettable %}{% include_relative generated/exceptions/things_id_notsettable.md %}{% endcapture %}
{{ things_id_notsettable | markdownify }}

{% capture things_policy_notallowed %}{% include_relative generated/exceptions/things_policy_notallowed.md %}{% endcapture %}
{{ things_policy_notallowed | markdownify }}

{% capture things_policyId_notallowed include_relative generated/exceptions/things_policyId_notallowed.md %}{% endcapture %}
{{ things_policyId_notallowed | markdownify }}

{% capture things_policyId_notmodifiable %}{% include_relative generated/exceptions/things_policyId_notmodifiable.md %}{% endcapture %}
{{ things_policyId_notmodifiable | markdownify }}

{% capture things_thing_conflict %}{% include_relative generated/exceptions/things_thing_conflict.md %}{% endcapture %}
{{ things_thing_conflict | markdownify }}

{% capture things_thing_notcreatable %}{% include_relative generated/exceptions/things_thing_notcreatable.md %}{% endcapture %}
{{ things_thing_notcreatable | markdownify }}

{% capture things_thing_notdeletable %}{% include_relative generated/exceptions/things_thing_notdeletable.md %}{% endcapture %}
{{ things_thing_notdeletable | markdownify }}

{% capture things_thing_notfound %}{% include_relative generated/exceptions/things_thing_notfound.md %}{% endcapture %}
{{ things_thing_notfound | markdownify }}

{% capture things_thing_notmodifiable %}{% include_relative generated/exceptions/things_thing_notmodifiable.md %}{%
endcapture %} {{ things_thing_notmodifiable | markdownify }}

{% capture things_thing_toomanymodifyingrequests %}{% include_relative
generated/exceptions/things_thing_toomanymodifyingrequests.md %}{% endcapture %} {{
things_thing_toomanymodifyingrequests | markdownify }}

{% capture things_thing_unavailable %}{% include_relative generated/exceptions/things_thing_unavailable.md %}{%
endcapture %} {{ things_thing_unavailable | markdownify }}

{% capture things_id_notdeletable %}{% include_relative generated/exceptions/things_id_notdeletable.md %}{% endcapture
%} {{ things_id_notdeletable | markdownify }}

{% capture things_policyId_notdeletable %}{% include_relative generated/exceptions/things_policyId_notdeletable.md %}{%
endcapture %} {{ things_policyId_notdeletable | markdownify }}
