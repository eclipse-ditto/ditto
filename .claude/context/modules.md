# Module Structure

The Ditto repository is organized as a multi-module Maven project:

```
ditto/
├── base/              # Base utilities (JSON, auth, headers, signals)
├── benchmark-tool/    # Performance benchmarking utilities
├── bom/               # Bill of Materials for dependency management
├── connectivity/      # Connectivity service (external messaging)
├── documentation/     # Project documentation (Jekyll site)
├── edge/              # Shared edge logic (Gateway + Connectivity)
├── gateway/           # Gateway service (HTTP/WS API)
├── internal/          # Internal shared utilities (actors, config, persistence)
├── json/              # JSON processing utilities
├── json-cbor/         # CBOR (binary JSON) support
├── jwt/               # JWT authentication model
├── legal/             # License headers and third-party notices
├── messages/          # Message model (for live messaging)
├── placeholders/      # Placeholder resolution for transformations
├── policies/          # Policies service + model
├── protocol/          # Ditto Protocol (JSON message format)
├── rql/               # RQL (Resource Query Language) parser
├── things/            # Things service + model
├── thingsearch/       # Things-Search service
├── ui/                # Explorer UI (TypeScript/SCSS)
├── utils/             # General utilities
├── wot/               # Web of Things (WoT) integration
└── deployment/        # Docker Compose, Kubernetes, etc.
```

## Core Modules Explained

### Service Modules

Each service module (things, policies, gateway, connectivity, thingsearch) typically contains:
- `model/` - Domain models, commands, events, responses
- `service/` - Service implementation with actors, routes, persistence

#### Model Modules - Public API ⚠️

**CRITICAL**: Model modules (e.g., `/things/model`, `/policies/model`) are **public API** of Ditto.

**Backward Compatibility Requirements**:
- Model classes are published as Maven artifacts and used by external clients
- Changes MUST be backward compatible
- Breaking changes require a major version bump of Ditto
- **Avoid breaking changes at all cost**

**What this means**:
- ✅ Add new methods with default implementations
- ✅ Add new optional fields
- ✅ Deprecate old methods (don't remove)
- ❌ Remove public methods
- ❌ Change method signatures
- ❌ Change serialization format
- ❌ Remove fields from JSON representation

**Examples of model modules**:
- `things/model/` - Thing, Feature, Attributes models
- `policies/model/` - Policy, Subject, Resource models
- `messages/model/` - Message models
- `base/model/` - Core signal abstractions
- `protocol/` - Ditto Protocol models

When modifying these modules, always consider:
1. Will this break existing clients?
2. Can this be done with default methods instead?
3. Should this be behind a feature toggle?
4. Does this require API versioning?

### Shared Utility Modules

**base/**
- Core abstractions used across all services
- Signal hierarchy (Command, Event, Response)
- DittoHeaders and header validation
- JSON processing
- Auth context and subjects

**internal/**
- Internal utilities not part of public API
- Actor utilities and patterns
- Config management
- Persistence patterns
- Pub-Sub infrastructure

**protocol/**
- Ditto Protocol specification
- JSON message format for all signals
- Protocol adapters for conversion

**json/**
- JSON API for creating and manipulating JSON
- JsonValue, JsonObject, JsonArray abstractions
- JSON field selectors

### Special Purpose Modules

**bom/** (Bill of Materials)
- Centralized dependency version management
- Used by other modules to ensure consistent versions

**legal/**
- License headers for all file types
- Third-party dependency notices
- Legal documentation

**deployment/**
- Docker Compose configurations
- Kubernetes manifests
- Deployment guides and scripts

**documentation/**
- Jekyll-based documentation site
- Hosted at https://www.eclipse.dev/ditto/

**ui/**
- Ditto Explorer web UI
- TypeScript/SCSS application
- Build with npm

## Module Dependencies

General dependency flow:
```
Services (things, policies, etc.)
    ↓
Internal utilities + Protocol + Base
    ↓
JSON + Utils
```

**Key principles:**
- Services depend on internal utilities but not on each other
- Model packages are independent and reusable
- Protocol module bridges between different signal types
- Base module provides foundational abstractions
