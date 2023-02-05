# Releasing Eclipse Ditto

Perform the following steps

## Plan the release **upfront** via the Eclipse projects page

Ditto releases are tracked and planned here: https://projects.eclipse.org/projects/iot.ditto/releases/

## Build Ditto release

* build Jenkins Ditto release job: https://ci.eclipse.org/ditto/
* After release was pushed on https://oss.sonatype.org:
   * (old way - should no longer be required starting with Ditto 3.1.0) Ditto artifacts are in a "Staging repositories" (which some of the team members have access to, e.g. Thomas, Dominik, Gerald, Johannes, ...): 
      * First close the staging repo (after all artifacts are there, e.g. also the client artifacts)
      * Then release the staging repo
      * Then it will take a few hours until those changes are synced successfully to Maven central
* Write Release notes, e.g. like for 3.1.0: https://www.eclipse.org/ditto/release_notes_310.html
   * New features, changes, bug fixes to last release / milestone release
   * Add migration notes (if there are any)
* Write a Blog post announcement, e.g. like for: https://www.eclipse.org/ditto/2022-12-16-release-announcement-310.html
* Close GitHub milestone (and assign all Issues/PRs which were still included in that milestone): https://github.com/eclipse-ditto/ditto/milestones
* Create a GitHub release: https://github.com/eclipse-ditto/ditto/releases (based on the Tags which was pushed during release job)
* Write a mail to the "ditto-dev" mailing list
* Tweet about it ;)
* Set binary compatibility check version to the new public release. Delete all exclusions and module-level deactivation of japi-cmp plugin except for *.internal packages.
* Update https://github.com/eclipse-ditto/ditto/blob/master/SECURITY.md with the supported versions to receive security fixes
* For major+minor versions:
   * Create a "release" branch"release-<version>" from the released git tag
      * needed to build the documentation from
      * required for bugfixes to build a bugfix release for the affected minor version
   * Add the new version to the documentation config: https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/_config.yml#L114
   * Adjust the "website" CI jobs to also build the newly added branch:
      * https://ci.eclipse.org/ditto/view/Website/
         * https://ci.eclipse.org/ditto/view/Website/job/website-build-and-deploy-fast/ will build the latest released minor version + "master" (development) version
         * https://ci.eclipse.org/ditto/view/Website/job/website-build-and-deploy-full/ will build documentation for all Ditto versions ever created - and will take very long
      * Adjust the Jenkins scripts via the Jenkins UI ("Configure") to checkout the new branch + build the docs from + zip it, unzip it, etc
