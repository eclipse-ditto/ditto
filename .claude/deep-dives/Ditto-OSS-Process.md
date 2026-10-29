# Eclipse Ditto and OSS Processes Deep Dive

## Overview

This deep dive covers the day-to-day open source processes and responsibilities for the Eclipse Ditto project. Understanding these processes is essential for all committers and contributors to maintain a healthy, compliant, and active OSS project.

## Goals

Understanding:
- Daily OSS development tasks
- Ditto Architecture Decision Records (DADRs)
- CI tools and infrastructure
- Website and blog management
- Third-party library management
- Semantic versioning strategy
- Public release creation process
- Eclipse Foundation processes
- Community interaction

## Daily Doing

### All Committers Responsibilities

**Code & Write Tests**:
- Follow Contribution Guidelines
- Write unit and integration tests
- Ensure code quality

**Add Documentation**:
1. **Public Ditto documentation** (markdown)
   - Location: https://github.com/eclipse-ditto/ditto/tree/master/documentation
   - Document all new features/changes
   - Undocumented features won't be found or used by community

2. **OpenAPI docs**
   - Location: https://github.com/eclipse-ditto/ditto/tree/master/documentation/src/main/resources/openapi
   - Rendered by swagger-ui at https://www.eclipse.dev/ditto/http-api-doc.html
   - Keep API documentation synchronized with implementation

**Critical rule**: A not documented Ditto feature will never be found and used by the community

## Ditto Architecture Decision Records (DADRs)

### Purpose

**For major architectural changes**: Document as ADRs

**Location**: https://github.com/eclipse-ditto/ditto/tree/master/documentation/src/main/resources/architecture

**Responsibility**: All committers

**When to create**:
- Significant architectural changes
- Technology choices
- Design pattern decisions
- Breaking changes

**Benefit**:
- Historical context preserved
- Decisions traceable
- Onboarding resource for new developers

## CI Tools

### Eclipse Foundation Jenkins

**URL**: https://ci.eclipse.org/ditto/

**Access**: Requires "Committer" role

**Infrastructure**: Shared OpenShift cluster
- Resources rather limited
- Managed by Eclipse Foundation
- Not under team control

**Purpose**:
- Build and test PRs
- Run release builds
- Publish to Eclipse Maven repository

**Special capability**: Maven signing
- Private keys required to sign .jar files
- Only configured on Eclipse CI
- Required for publishing to Maven Central

### Build Jobs

**PR validation**:
- Compile code
- Run tests
- Check code quality

**Release publishing**:
- Sign artifacts
- Publish to Eclipse Maven: https://repo.eclipse.org/content/repositories/ditto-releases/
- Publish to Maven Central

## Website + Blog

### Responsibilities

**All committers** maintain the website

**Tasks**:
- Keep website "in tact"
- Adapt to Eclipse Foundation changes
  - Example: Migration from eclipse.org/<project> to <project>.eclipseprojects.io

**Blogpost topics**:
1. **New releases** of Ditto
2. **Noteworthy features** requiring more than documentation
3. **Presentations/webinars** you want to announce publicly
4. **Community updates** and announcements

### Publishing Process

**CI job**: https://ci.eclipse.org/ditto/view/Website/

**Workflow**:
1. Edit website content in repository
2. Commit to master branch
3. CI automatically publishes

**Location**: Website part of main Ditto repository

## Third Party Libraries

### Responsibilities

**All committers + especially project lead**

### License Compatibility

**Critical rule**: Ensure compatible license when adding libraries

**Never add**:
- Strong copyleft licensed libraries (GPL2, GPL3)
- Should be knowledge from mandatory OSS training

**Tool**: ClearlyDefined - https://clearlydefined.io/
- Check score of new libraries
- Score > 75: Can be added without additional steps
- Score ≤ 75: Requires Eclipse Foundation approval

### License Checks in CI

**Jobs**: https://ci.eclipse.org/ditto/view/License%20checks/

**Checks for**:
1. **Ditto main repo**: license-check-0-ditto
2. **Ditto Java Client**: license-check-1-ditto-client-java
3. **Ditto JavaScript Client**: license-check-2-ditto-client-javascript

**Runs on**: Master branch

**Uses**: Eclipse Foundation "dash-licenses" tool

**Process**:
1. Gathers all dependencies
2. Checks ClearlyDefined score
3. Reports failures

**Before public release**: All 3 builds must be "green"

### Handling Failures

**Re-run strategy**:
- ClearlyDefined retrieves scores lazily
- Re-running might fix issues as scores calculated

**Auto-review job**: license-check-0-ditto-with-auto-review

**Use with care**:
- Takes GitLab token (from your Eclipse Foundation GitLab account)
- Automatically creates issues for failed dependencies
- Issues created here: https://gitlab.eclipse.org/eclipsefdn/emo-team/iplab/-/issues
- Can produce spam if misused (e.g., already cleared dependencies)

## Semantic Versioning

### Strategy

**Ditto applies Semantic Versioning**

**Version format**: MAJOR.MINOR.PATCH (e.g., 2.4.0)

### Version Types

**Bugfixes (Patch/Micro)**: 2.4.x
- Only bugfixes
- No new features
- Backward compatible

**New Features (Minor)**: 2.x.0
- New features that are API compatible
- No breaking changes to public APIs
- Backward compatible

**API Breaking (Major)**: x.0.0
- Breaking changes to public APIs
- Non-backward compatible

### HTTP APIs and JSON Formats

**Theory**: Could change for major versions (2.x.x to 3.x.x)

**Practice**: Must stay backward compatible "forever"
- HTTP API and JSON format must remain backward compatible

### Public API Definition

**DADR**: What is treated as "public API"
- Location: https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md
- Clearly defines what must remain backward compatible

### Enforcement

**Maven plugin**: Enforces backward compatibility in Java modules
- Automatically checks for breaking changes
- Build fails if public API broken
- Configured in pom.xml

## Creating Public Ditto Releases

### Release Frequency Goals

**Bugfix ("micro") releases**: As needed
- When critical bug affects community
- Community reported issues

**"Minor" releases**: 3-4 per year
- New features
- Enhancements
- Non-critical fixes

**"Major" releases**: Every 1-2 years
- Significant changes
- Potential breaking changes (though rare in practice)

### Prior to Release

**1. Plan via project site**:
- URL: https://projects.eclipse.org/projects/iot.ditto
- "Create a new release" (requires Committer role)
- Fill out estimated release date (adjustable later)
- Look at other planned releases for guidance

**2. Prepare content**:
- List all features/changes
- Document major bugfixes
- Prepare release notes
- Write blogpost for website

### For the Release

**1. Ensure License Checks green**:
- All 3 license check builds passing on master

**2. GitHub Milestone**:
- Assign all included PRs and Issues
- Close milestone: https://github.com/eclipse-ditto/ditto/milestones

**3. Trigger release builds**:
- URL: https://ci.eclipse.org/ditto/view/Releases/
- Publishes to Eclipse Maven + Maven Central
- JS client published to npm

**4. Docker images**:
- Tag automatically created in GitHub
- GitHub Action builds and publishes
- Destination: https://hub.docker.com

**5. Post-release jobs**:
- Maven Central closing
- Blogpost publishing
- GitHub release creation
- Announcements
- See: "Building a public Ditto release" documentation

### After the Release

**1. Update binary compatibility check version**:
- File: pom.xml
- Example: https://github.com/eclipse-ditto/ditto/blob/master/pom.xml#L235
- Java client: https://github.com/eclipse-ditto/ditto-clients/blob/master/java/pom.xml#L242
- Ensures no public Java APIs broken for next minor release

**2. Create new GitHub Milestone**:
- URL: https://github.com/eclipse-ditto/ditto/milestones
- Assign new Issues/PRs to it

## Eclipse Processes / Handbook

### Project Lead Responsibilities

**Eclipse Project Handbook**: https://www.eclipse.org/projects/handbook/

**Key processes**:
- Release planning: https://www.eclipse.org/projects/handbook/#release
- Progress/Release reviews: https://www.eclipse.org/projects/handbook/#release-review

### Progress/Release Reviews

**Frequency**: Once a year for graduated projects

**Last one (2022/05)**:
- Project page: https://projects.eclipse.org/projects/iot.ditto/reviews/2022.05-progress-review
- GitLab issue: https://gitlab.eclipse.org/eclipsefdn/emo-team/emo/-/issues/271

**After successful review**:
- Project "allowed" to release whenever needed for next year

**Responsibility**: Project lead or committer

**Checks**:
- Project reacts on communication channels
- Responds to questions, issues, PRs
- Active development
- Community health

**Advice**: When in doubt, consult Handbook

## Interacting with the Community

### Committers Responsibilities

**Subscribe to feedback channels**:
- GitHub issues
- Gitter chat
- Mailing lists
- Stack Overflow (ditto tag)

**Community advocate**:
- Rotate weekly (or similar schedule)
- Treat community as customer
- Reply to questions
- Respond to feature requests
- Engage with issues and PRs

**Goal**: Active, welcoming community

## Project Lead Responsibilities

### Project Page Maintenance

**URL**: https://projects.eclipse.org/projects/iot.ditto

**Tasks**:
- Keep up-to-date
- Update scope when it changes
- Use for Eclipse processes:
  - Committer elections
  - Release creation

### Eclipse IoT Working Group

**Monthly meetings**:
- Check mailing list for agenda: https://www.eclipse.org/lists/iot-wg/
- Subscribe to get invitations
- Represent Ditto project
- Join discussions

**Responsibilities**:
- Attend meetings
- Provide project updates
- Collaborate with other IoT projects

### Status Presentations

**EclipseCon Europe Community Day**:
- Default yearly venue for project update
- Location: Ludwigsburg, Germany
- Present project status
- Example: https://www.eclipse.dev/ditto/slides/2021_10_25-eclipse-iot-wg-community-day/

**Additional presentations**:
- During Eclipse IoT WG monthly meetings
- At least once per year
- Twice per year recommended for active projects

### Project Promotion

**Seek presentation opportunities**:
- Conferences
- Meetups
- Webinars
- Example presentations: https://www.eclipse.dev/ditto/presentations.html

**Collaborations**:
- Open to other communities
- Broaden adoption and reach
- Example: W3C Web of Things integration
  - Outcome: https://www.w3.org/WoT/developers/#runtime-expose

**Adopter listings**:
- When learning about adoption, ask to be listed
- URL: https://iot.eclipse.org/adopters/?#iot.ditto
- Increases project visibility

## Key Takeaways

### Daily Operations

**Code quality**:
- Follow contribution guidelines
- Write tests
- Document everything

**Community first**:
- Undocumented features won't be used
- Respond to community questions
- Engage with issues and PRs

### Compliance

**License management**:
- Check all new dependencies
- Use ClearlyDefined scores
- Green license checks before release

**Semantic versioning**:
- Backward compatibility critical
- Maven plugin enforcement
- Clear public API definition

### Release Process

**Planning**:
- Use Eclipse project site
- Assign to milestones
- Prepare documentation

**Execution**:
- Green license checks mandatory
- Trigger CI release jobs
- Publish Docker images
- Update Helm charts

**Communication**:
- Blog posts
- GitHub releases
- Mailing list announcements

### Leadership

**Project lead**:
- Maintain project page
- Attend Eclipse IoT WG meetings
- Present status updates
- Promote project externally
- Foster collaborations

**All committers**:
- Act as community advocates
- Maintain website
- Create ADRs for major decisions
- Keep CI infrastructure working

### Eclipse Foundation

**Processes**:
- Annual progress reviews
- Release planning
- Committer elections

**Resources**:
- Eclipse CI (limited resources)
- Eclipse Maven repository
- Eclipse Foundation handbook

## References

- Eclipse Ditto repository: https://github.com/eclipse-ditto/ditto
- Eclipse CI: https://ci.eclipse.org/ditto/
- Eclipse Project page: https://projects.eclipse.org/projects/iot.ditto
- Eclipse Maven repo: https://repo.eclipse.org/content/repositories/ditto-releases/
- ClearlyDefined: https://clearlydefined.io/
- Eclipse Project Handbook: https://www.eclipse.org/projects/handbook/
- DADR on semantic versioning: DADR-0005-semantic-versioning.md
- Eclipse IoT Packages: https://github.com/eclipse/packages
- Eclipse IoT WG mailing list: https://www.eclipse.org/lists/iot-wg/
