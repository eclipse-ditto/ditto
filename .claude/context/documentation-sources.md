# Documentation Sources

The Ditto repository contains extensive documentation that provides detailed information about concepts, APIs, and architecture decisions. When working with Ditto, consult these documentation sources for authoritative information.

## Main Documentation

### Location
`documentation/src/main/resources/pages/ditto/`

This directory contains the complete Ditto documentation in Markdown format, organized by topic.

### Key Documentation Files

**Architecture & Overview:**
- `architecture-overview.md` - High-level architecture overview
- `architecture-services-things.md` - Things service architecture
- `architecture-services-policies.md` - Policies service architecture
- `architecture-services-gateway.md` - Gateway service architecture
- `architecture-services-connectivity.md` - Connectivity service architecture
- `architecture-services-things-search.md` - Things-Search service architecture

**Basic Concepts:**
- `basic-overview.md` - Introduction to Ditto concepts
- `basic-thing.md` - Thing entity model
- `basic-policy.md` - Policy model and authorization
- `basic-feature.md` - Feature concept
- `basic-auth.md` - Authentication mechanisms
- `basic-connections.md` - Connectivity connections
- `basic-search.md` - Search capabilities
- `basic-rql.md` - RQL query language
- `basic-messages.md` - Message pattern (live messaging)
- `basic-acknowledgements.md` - Acknowledgement pattern
- `basic-changenotifications.md` - Change notifications
- `basic-namespaces-and-names.md` - Naming conventions
- `basic-placeholders.md` - Placeholder system
- `basic-enrichment.md` - Signal enrichment
- `basic-history.md` - Historical access to entities
- `basic-metadata.md` - Metadata on entities

**Protocol Specifications:**
- `protocol-specification.md` - Ditto Protocol specification
- `protocol-specification-topic.md` - Topic structure
- `protocol-specification-acks.md` - Acknowledgement protocol
- `protocol-specification-streaming-subscription.md` - Streaming subscriptions
- `protocol-twinlive.md` - Twin vs Live channels

**Connectivity:**
- `connectivity-*.md` - Various connectivity topics
- `connectivity-protocol-bindings-mqtt.md` - MQTT binding
- `connectivity-protocol-bindings-amqp*.md` - AMQP bindings
- `connectivity-protocol-bindings-hono.md` - Eclipse Hono integration
- `connectivity-mapping.md` - Message mapping
- `connectivity-tls-certificates.md` - TLS configuration
- `connectivity-ssh-tunneling.md` - SSH tunnel setup

**HTTP API:**
- `httpapi-*.md` - HTTP API documentation for various resources

**Signals:**
- `basic-signals-command.md` - Command signals
- `basic-signals-event.md` - Event signals
- `basic-signals-commandresponse.md` - Response signals
- `basic-signals-errorresponse.md` - Error responses
- `basic-signals-announcement.md` - Announcement signals

**Release Notes:**
- `release_notes_*.md` - Release notes for each version

### Usage
When implementing features or understanding concepts, search this directory for relevant documentation:

```bash
# Example: Find all documentation about policies
grep -r "policy" documentation/src/main/resources/pages/ditto/*.md

# Example: Find signal-related documentation
ls documentation/src/main/resources/pages/ditto/basic-signals-*.md
```

## OpenAPI Specifications

### Location
`documentation/src/main/resources/openapi/`

Contains OpenAPI 2.0 (Swagger) specifications for the Ditto HTTP API.

### Key Files
- `ditto-api-2.yml` - Complete OpenAPI specification for Ditto's HTTP/REST API
- `sources/` - Source files used to generate the OpenAPI spec

### Usage
- Reference for HTTP API endpoint definitions
- Request/response schemas
- API parameter documentation
- Authentication requirements

**Viewing:**
```bash
# View the OpenAPI spec
cat documentation/src/main/resources/openapi/ditto-api-2.yml

# Or use Swagger UI for interactive exploration
# (specification is available at runtime at /apidoc)
```

## JSON Schemas

### Location
`documentation/src/main/resources/jsonschema/`

Contains JSON Schema definitions for Ditto data structures.

### Key Files
- `thing_v2.json` - JSON Schema for Thing entity
- `policy.json` - JSON Schema for Policy entity
- `feature_v2.json` - JSON Schema for Feature
- `connection.json` - JSON Schema for Connection
- `protocol-envelope.json` - Ditto Protocol envelope
- `protocol-*.json` - Various protocol-related schemas
- `error.json` - Error response schema

### Usage
- Validate JSON payloads
- Understand exact structure of entities
- Generate client code
- API documentation reference

**Example:**
```bash
# View Thing schema
cat documentation/src/main/resources/jsonschema/thing_v2.json
```

## Architecture Decision Records (ADRs)

### Location
`documentation/src/main/resources/architecture/`

Contains Architecture Decision Records documenting significant architectural decisions.

### Format
Files are named `DADR-####-short-title.md` (Ditto Architecture Decision Record)

### Key ADRs
- `DADR-0001-record-architecture-decisions.md` - Why we use ADRs
- `DADR-0002-replace-akka-pubsub-for-event-publishing.md` - Event publishing pattern
- `DADR-0003-do-not-interrupt-threads.md` - Thread handling policy
- `DADR-0004-signal-enrichment.md` - Signal enrichment design
- `DADR-0005-semantic-versioning.md` - Versioning strategy
- `DADR-0006-merge-payload.md` - Merge Things feature design
- `DADR-0007-concierge-removal.md` - Service simplification
- `DADR-0008-wildcard-search-index.md` - Search index implementation

### Usage
When making architectural decisions or understanding why certain patterns exist:

```bash
# List all ADRs
ls documentation/src/main/resources/architecture/

# Read specific ADR
cat documentation/src/main/resources/architecture/DADR-0004-signal-enrichment.md
```

**ADR Structure:**
- **Status**: accepted/rejected/deprecated/superseded
- **Context**: Background and problem
- **Decision**: What was decided
- **Consequences**: Implications of the decision

## Best Practices for Using Documentation

### When Implementing Features
1. Check relevant `basic-*.md` files for concept understanding
2. Review `architecture-services-*.md` for service-specific patterns
3. Consult `protocol-*.md` for message format details
4. Check ADRs for architectural context

### When Understanding Existing Code
1. Search documentation for related concepts
2. Read relevant ADRs to understand "why"
3. Check JSON schemas for exact data structures
4. Review OpenAPI spec for HTTP API contracts

### When Adding New Features
1. Check if similar features exist (search docs)
2. Review related ADRs for patterns to follow
3. Consider if a new ADR is needed for significant decisions
4. Update relevant documentation files

### When Fixing Bugs
1. Check issue documentation in basic/advanced topics
2. Review signal documentation for expected behavior
3. Consult OpenAPI spec for API contracts
4. Check ADRs for intentional design choices

## Documentation Build

The documentation is built using Jekyll and published at https://www.eclipse.dev/ditto/

To build locally:
```bash
cd documentation/
# Follow instructions in documentation/README.md
```

## Documentation Website Structure

Published at: https://www.eclipse.dev/ditto/

The website mirrors the structure in `pages/ditto/` with additional navigation and search capabilities.
