# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Eclipse Ditto is a digital twin framework for IoT, implementing a microservices architecture using Apache Pekko (an Akka fork maintained by the Apache Software Foundation), event sourcing, and CQRS patterns. The codebase is primarily Java 21 with Maven as the build system.

## Quick Reference

This repository is organized with detailed context in the `.claude/context/` directory. Each file covers a specific aspect of working with Ditto.

### Essential Documentation

📖 **[Build & Test Commands](.claude/context/build-and-test.md)**
- How to build the project
- Running tests (unit, integration, specific tests)
- Local development environment setup
- Code quality checks

🏗️ **[Architecture Overview](.claude/context/architecture.md)**
- Core microservices and their responsibilities
- Inter-service communication patterns
- Key architectural patterns (Event Sourcing, CQRS, Actor Supervision)
- Technology stack

🎚️ **[Feature Toggle System](.claude/context/feature-toggles.md)** ⚠️ **CRITICAL**
- Why feature toggles are required for new features
- How to add a feature toggle
- Configuration and usage

💻 **[Code Patterns & Conventions](.claude/context/code-patterns.md)** ⚠️ **Java 8 for API modules**
- Signal pattern (Commands/Events/Responses)
- Immutable model objects
- Persistence actor pattern
- **Actor concurrency** - NEVER block or modify state in CompletableFuture lambdas
- **Java 8 compatibility for public API modules** (model, protocol, json, rql)
- **Configuration management** (HOCON + Helm updates required)
- Test organization
- Naming conventions
- License headers
- Code style guide

🔀 **[Git Workflow](.claude/context/git-workflow.md)** ⚠️ **CRITICAL**
- Eclipse Foundation contribution requirements (ECA, signed commits)
- GitHub issue-first development process
- PR creation and approval workflow
- Commit message format

📦 **[Module Structure](.claude/context/modules.md)**
- Repository organization
- Core modules and their purposes
- Module dependencies

🔧 **[Troubleshooting](.claude/context/troubleshooting.md)**
- Common build issues
- Test failures
- Docker/deployment problems
- Runtime issues

📚 **[Documentation Sources](.claude/context/documentation-sources.md)**
- Main documentation pages (architecture, concepts, APIs)
- OpenAPI specifications
- JSON schemas
- Architecture Decision Records (ADRs)

🚀 **[Deployment Options](.claude/context/deployment.md)**
- Helm deployment (production-ready, actively maintained)
- Docker Compose (local development)
- Kubernetes, OpenShift, Azure options
- Monitoring and operations (Grafana, Prometheus)

### Architecture Deep Dives

🔬 **[Deep Dives](.claude/deep-dives/)** - Core architecture and design decisions
- **Start here**: [README.md](.claude/deep-dives/README.md) - Guide to deep-dive documents
- Key topics: Concierge removal, twin updates/events, pub/sub, serialization, acknowledgements
- Cross-cutting concerns: Authorization, enrichment, placeholders, connections
- Use when: Understanding architectural decisions, debugging complex flows, working on core features

📄 **[Extended Docs](.claude/docs/)** - Feature-specific documentation
- Query & Search: RQL syntax, search service architecture
- Messaging: Live messaging patterns
- Connections: Kafka, payload mapping, log forwarding
- Specialized features: WoT integration, OSS process

## Getting Started

1. **First Time Setup**: Read [Build & Test](.claude/context/build-and-test.md) to set up your environment
2. **Understand the System**: Review [Architecture](.claude/context/architecture.md) for the big picture
3. **Deep Dive**: Consult [Documentation Sources](.claude/context/documentation-sources.md) for detailed concepts
4. **Deployment**: Check [Deployment Options](.claude/context/deployment.md) for running Ditto
5. **Before Contributing**: Read [Git Workflow](.claude/context/git-workflow.md) for contribution requirements
6. **Writing Code**: Follow [Code Patterns](.claude/context/code-patterns.md) for consistency
7. **Adding Features**: Always use [Feature Toggles](.claude/context/feature-toggles.md)

## Key Requirements

### For Contributors

⚠️ **MUST DO** before contributing:
1. Sign Eclipse Contributor Agreement (ECA)
2. Create GitHub issue before starting work
3. Use feature toggles for new features which would change behavior of existing functionality
4. Sign all commits with `-s` flag
5. Include license headers in new files
6. **Maintain backward compatibility in model modules** (`/things/model`, `/policies/model`, etc.) - these are public API
7. **Use Java 8 syntax in public API modules** - see [Code Patterns](.claude/context/code-patterns.md#java-version-compatibility)
8. **Update Helm when adding configuration** - changes to HOCON config require corresponding Helm updates in `deployment/helm/ditto/`
9. **Ask for `@since` version** - when adding public API (classes/methods in model modules), ask the user for the version number
10. **Provide unit tests** - always include tests for generated code, covering happy path AND corner cases (use Pekko TestKit for actors)

### Main Branch

Use `master` as the base branch for all PRs.

## Quick Commands

```bash
# Build everything
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests for a module
mvn test -pl things/service

# Start local Ditto
cd deployment/docker/ && docker-compose up -d

# Check license headers
mvn license:check
```

See [Build & Test Commands](.claude/context/build-and-test.md) for complete command reference.

## Project Resources

- **Documentation**: https://www.eclipse.dev/ditto/
- **Explorer UI**: https://eclipse-ditto.github.io/ditto/
- **GitHub Issues**: https://github.com/eclipse-ditto/ditto/issues
- **Gitter Chat**: https://gitter.im/eclipse/ditto
- **System Tests**: https://github.com/eclipse-ditto/ditto-testing

## Questions or Issues?

1. Check [Troubleshooting](.claude/context/troubleshooting.md) for common problems
2. Search [GitHub Issues](https://github.com/eclipse-ditto/ditto/issues)
3. Ask on [Gitter Chat](https://gitter.im/eclipse/ditto)
4. Create a new GitHub issue with details

---

**Note**: This is a high-level overview. For detailed information, see the specific context files in `.claude/context/`.
