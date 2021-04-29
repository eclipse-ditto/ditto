---
title: Policies error responses
keywords: examples, policies error responses, error responses
search: exclude
permalink: protocol-examples-policies-errorresponses.html
---

The following listed example errors do not claim to be a complete list of all available and possible error responses.  
Also, their texts and **error** codes might be outdated.

In order to find out all currently available errors, please inspect Ditto's codebase and look at all implementations of
the `PolicyException` interface.

{% capture policy_id_invalid %}{% include_relative generated/exceptions/policies_id_invalid.md %}{% endcapture %}
{{ policy_id_invalid | markdownify }}

{% capture policies_policy_conflict %}{% include_relative generated/exceptions/policies_policy_conflict.md %}{% endcapture %}
{{ policies_policy_conflict | markdownify }}

{% capture policies_policy_modificationinvalid %}{% include_relative generated/exceptions/policies_policy_modificationinvalid.md %}{% endcapture %}
{{ policies_policy_modificationinvalid | markdownify }}

{% capture policies_policy_notfound %}{% include_relative generated/exceptions/policies_policy_notfound.md %}{% endcapture %}
{{ policies_policy_notfound | markdownify }}

{% capture policies_policy_notmodifiable %}{% include_relative generated/exceptions/policies_policy_notmodifiable.md %}{% endcapture %}
{{ policies_policy_notmodifiable | markdownify }}

{% capture policies_policy_toomanymodifyingrequests %}{% include_relative generated/exceptions/policies_policy_toomanymodifyingrequests.md %}{% endcapture %}
{{ policies_policy_toomanymodifyingrequests | markdownify }}

{% capture policies_policy_unavailable %}{% include_relative generated/exceptions/policies_policy_unavailable.md %}{% endcapture %}
{{ policies_policy_unavailable | markdownify }}

{% capture policies_entry_invalid %}{% include_relative generated/exceptions/policies_entry_invalid.md %}{% endcapture %}
{{ policies_entry_invalid | markdownify }}

{% capture policies_entry_modificationinvalid %}{% include_relative generated/exceptions/policies_entry_modificationinvalid.md %}{% endcapture %}
{{ policies_entry_modificationinvalid | markdownify }}

{% capture policies_entry_notfound %}{% include_relative generated/exceptions/policies_entry_notfound.md %}{% endcapture %}
{{ policies_entry_notfound | markdownify }}

{% capture policies_entry_notmodifiable %}{% include_relative generated/exceptions/policies_entry_notmodifiable.md %}{% endcapture %}
{{ policies_entry_notmodifiable | markdownify }}

{% capture policies_subjects_notfound %}{% include_relative generated/exceptions/policies_subjects_notfound.md %}{% endcapture %}
{{ policies_subjects_notfound | markdownify }}

{% capture policies_subjects_notmodifiable %}{% include_relative generated/exceptions/policies_subjects_notmodifiable.md %}{% endcapture %}
{{ policies_subjects_notmodifiable | markdownify }}

{% capture policies_subject_notfound %}{% include_relative generated/exceptions/policies_subject_notfound.md %}{% endcapture %}
{{ policies_subject_notfound | markdownify }}

{% capture policies_subject_notmodifiable %}{% include_relative generated/exceptions/policies_subject_notmodifiable.md %}{% endcapture %}
{{ policies_subject_notmodifiable | markdownify }}

{% capture policies_subjectid_invalid %}{% include_relative generated/exceptions/policies_subjectid_invalid.md %}{% endcapture %}
{{ policies_subjectid_invalid | markdownify }}

{% capture policies_resources_notfound %}{% include_relative generated/exceptions/policies_resources_notfound.md %}{% endcapture %}
{{ policies_resources_notfound | markdownify }}

{% capture policies_resources_notmodifiable %}{% include_relative generated/exceptions/policies_resources_notmodifiable.md %}{% endcapture %}
{{ policies_resources_notmodifiable | markdownify }}

{% capture policies_resource_notfound %}{% include_relative generated/exceptions/policies_resource_notfound.md %}{% endcapture %}
{{ policies_resource_notfound | markdownify }}

{% capture policies_resource_notmodifiable %}{% include_relative generated/exceptions/policies_resource_notmodifiable.md %}{% endcapture %}
{{ policies_resource_notmodifiable | markdownify }}


