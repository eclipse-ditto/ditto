---
title: Release notes 3.5.6
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.6 of Eclipse Ditto, released on 17.05.2024"
permalink: release_notes_356.html
---

This is a security bugfix release, no new features since [3.5.5](release_notes_355.html) were added.

## Changelog

Compared to the latest release [3.5.5](release_notes_355.html), the following changes and bugfixes were added.

### Security fixes


#### Security fix for CVE-2024-5165

The Eclipse Ditto's Web-UI, the [Explorer User Interface](https://eclipse.dev/ditto/user-interface.html), was vulnerable 
to Cross-Site Scripting (XSS) at multiple input fields.  
Affected versions are all Ditto-UI versions starting from when the Ditto-UI was introduced, with Ditto 
[3.0.0](release_notes_300.html#new-ditto-explorer-ui).

This is tracked through CVE [https://nvd.nist.gov/vuln/detail/CVE-2024-5165](https://nvd.nist.gov/vuln/detail/CVE-2024-5165).

The issue was detected and reported by [Manuel Sommer](https://gitlab.eclipse.org/manuelsommer) and 
[Quirin Zießler](https://gitlab.eclipse.org/quirinziessler) and disclosed via the 
[Eclipse Vulnerability Reporting](https://www.eclipse.org/security/) process.  
We like to thank them for the detection and the effort of reporting the affected input fields.

For any users of Eclipse Ditto who deployed also the Ditto Web-UI, we recommend updating the Web-UI.  
If the Web-UI is not deployed, no action to update is needed, as the **Ditto backend is not affected**.
