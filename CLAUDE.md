# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Eclipse Ditto is a digital twin framework for IoT, implementing a microservices architecture using Apache Pekko (an Akka fork maintained by the Apache Software Foundation), event sourcing, and CQRS patterns. The codebase is primarily Java 25 with Maven as the build system.

Five microservices communicate via Pekko Cluster (no HTTP between services): **Things**, **Policies**, **Gateway**, **Connectivity**, and **Things-Search**. Key patterns: Event Sourcing via `AbstractPersistenceActor`, CQRS with Strategy pattern, Pekko Cluster Sharding by entity ID.

## Build & Test Commands

```bash
# Build without tests (fast)
mvn clean install -DskipTests

# Run tests for a module (always use -T4 for parallel execution)
mvn test -T4 -pl things/service

# Run a specific test class / method
mvn test -T4 -Dtest=ThingPersistenceActorTest
mvn test -T4 -Dtest=ThingPersistenceActorTest#testCreateThing

# Run integration tests
mvn verify -T4 -pl connectivity/service

# Check/fix license headers
mvn license:check
mvn license:format

# Start local Ditto via Docker Compose
cd deployment/docker/ && docker-compose up -d
```

## Key Requirements

1. **Feature toggles** for new features changing existing behavior - see [feature-toggles.md](.claude/context/feature-toggles.md)
2. **Java 8 syntax** in public API modules (model, protocol, json, rql) - see [code-patterns.md](.claude/context/code-patterns.md)
3. **Backward compatibility** in model modules - these are public API
4. **Helm updates** when adding HOCON configuration - see [code-patterns.md](.claude/context/code-patterns.md)
5. **`@since` version tag** on new public API - ask the user for the version number
6. **Unit tests** covering happy path AND corner cases (use Pekko TestKit for actors)
7. **License headers** on all new files (EPL 2.0, current year)
8. **Actor concurrency** - NEVER block or modify state in CompletableFuture lambdas - see [code-patterns.md](.claude/context/code-patterns.md)

## Contribution Workflow

1. Create GitHub issue before starting work
2. Create draft PR early for feedback
3. Branch naming: `feature/<desc>`, `bugfix/<desc>`, `refactor/<desc>`
4. Base branch: `master`

See [git-workflow.md](.claude/context/git-workflow.md) for full details.

## Context Files

Detailed guidance lives in `.claude/context/`:

- **[architecture.md](.claude/context/architecture.md)** - Service details, inter-service communication, technology stack
- **[code-patterns.md](.claude/context/code-patterns.md)** - Signals, persistence actors, actor concurrency, Java 8 modules, config management, WoT ThingModels, code style
- **[modules.md](.claude/context/modules.md)** - Repository structure, module dependencies, backward compatibility
- **[feature-toggles.md](.claude/context/feature-toggles.md)** - How to add and use feature toggles
- **[build-and-test.md](.claude/context/build-and-test.md)** - Complete build, test, and Docker commands
- **[git-workflow.md](.claude/context/git-workflow.md)** - ECA, commit format, PR process, branch naming
- **[deployment.md](.claude/context/deployment.md)** - Helm, Docker Compose, Kubernetes deployment
- **[troubleshooting.md](.claude/context/troubleshooting.md)** - Common build, test, Docker, and runtime issues
- **[documentation-sources.md](.claude/context/documentation-sources.md)** - OpenAPI specs, JSON schemas, ADRs

For architecture deep dives, see [.claude/deep-dives/README.md](.claude/deep-dives/README.md).

## Project Resources

- **Documentation**: https://www.eclipse.dev/ditto/
- **Explorer UI**: https://eclipse-ditto.github.io/ditto/
- **GitHub Issues**: https://github.com/eclipse-ditto/ditto/issues
- **System Tests**: https://github.com/eclipse-ditto/ditto-testing
