# W3C Web of Things (WoT) Deep Dive

## Overview

This deep dive covers the integration of W3C Web of Things (WoT) standard with Eclipse Ditto as a replacement for Eclipse Vorto.  
WoT provides a standardized way to describe IoT device capabilities and is seeing broad industry adoption.  
This integration enables skeleton generation, Thing Description generation, and improved interoperability.

## Goals

Understanding:
- Problem statement and use case for modeling in Ditto
- History with Vorto and why it's being replaced
- W3C WoT standard and its components
- WoT Thing Models (TMs) vs Thing Descriptions (TDs)
- Mapping of WoT concepts to Ditto concepts
- Ditto integration: Skeleton creation and TD generation
- WoT Java SDK provided by Ditto

## Problem Statement / Use Case

Eclipse Ditto lacks a built-in type system for declaring property datatypes, constraints, and operation signatures. Eclipse Vorto was the designated modeling tool but reached end-of-life in 2020. The Ditto community collaborated with W3C to adopt Web of Things as the replacement modeling standard. This collaboration resulted in a proof-of-concept integration and enhancements to WoT Thing Models based on Ditto needs (e.g., [composition support](https://github.com/w3c/wot-thing-description/issues/1177)).

## A Look into the Future: Web of Things

### Overview

**W3C**: "The web" standardization organization

**WoT (Web of Things)**: Broad industry support
- Intel, Siemens, Oracle, Microsoft, Fujitsu
- Active working group
- Standardization progressing

**Microsoft joining**: Recently to consolidate their DTDL (Digital Twin Definition Language) with WoT

**Promise**: Most promising standard to evolve about IoT interoperability and device capability modeling

### Documentation

**In a nutshell**: https://www.w3.org/WoT/documentation/

**Published Recommendations**:
- Architecture: https://www.w3.org/TR/wot-architecture/
- Thing Description: https://www.w3.org/TR/wot-thing-description11/

**Working Drafts** (not yet Recommendations):
- Discovery: https://www.w3.org/TR/wot-discovery/
- Profile: https://www.w3.org/TR/wot-profile/
- Scripting API: https://www.w3.org/TR/wot-binding-templates/
- Binding Templates: https://www.w3.org/TR/wot-binding-templates/

### Technical Foundation: JSON-LD

**JSON-LD**: JSON representation of RDF (Linked Data)
- W3C Standard: https://www.w3.org/TR/json-ld11/
- Primer: https://www.cloudbees.com/blog/json-ld-building-meaningful-data-apis

**Benefits**:
- Adds semantic context to models
- Links to other ontologies
  - Units: http://www.ontology-of-units-of-measure.org/page/om-2
  - Geolocation: https://www.w3.org/2003/01/geo/
- References established standards instead of "reinventing"
- Example: JsonSchema Draft-7 for JSON types and constraints

**Heavily builds on**:
- HTTP and "links"
- Web standards and practices

### Protocol Bindings

**Well supported protocols**:
- HTTP
- CoAP
- MQTT

**Various protocol bindings** supported conceptually

**Binding Templates**: Define how to use WoT with specific protocols

## WoT Thing Models (TMs)

### Overview

**Purpose**: Equivalent of Vorto models (functionblocks / informationmodels)

**Spec**: https://www.w3.org/TR/wot-thing-description11/#thing-model

**Templates**: Act as templates for deriving Thing Descriptions (TDs)

### Affordances

**Three types of affordances**:

**1. Property Affordances**:
- Define available property names
- Property "type" (JSON Schema)
- Human-readable "title" and "description"
- Optional "unit" specification
- Spec: https://www.w3.org/TR/wot-thing-description11/#propertyaffordance

**2. Action Affordances**:
- Define actions (operations/commands) to invoke on device
- "Input" and "output" data schemas
- Whether action is idempotent
- Spec: https://www.w3.org/TR/wot-thing-description11/#actionaffordance

**3. Event Affordances**:
- Define events emitted by device
- "Data" schema contained in event
- Spec: https://www.w3.org/TR/wot-thing-description11/#eventaffordance

### Inheritance and Composition

**TM Inheritance**:
- TMs can "extend" other TMs
- Reuse and specialize capabilities
- Spec: https://www.w3.org/TR/wot-thing-description11/#thing-model-extension-import

**TM Composition**:
- TMs can define "submodel" TMs
- Build complex models from simpler ones
- **Added based on Ditto input!**
- Spec: https://www.w3.org/TR/wot-thing-description11/#thing-model-composition

### Template Nature

**Common information only**:
- Information common for all devices of same "type" or class
- No instance-specific information
- No IDs identifying an instance
- No security information
- No protocol "forms"

**Derivation to TDs**:
- TMs act as templates
- TDs derived from TMs
- TDs contain instance-specific information

### Example Thing Model

**Example models**: https://github.com/eclipse-ditto/ditto-examples/tree/master/wot/models

**Sample TM** (Switchable):
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "time": "http://www.w3.org/2006/time#"
    }
  ],
  "@type": "tm:ThingModel",
  "title": "Switchable",
  "version": {
    "model": "1.0.0"
  },
  "tm:required": [],
  "properties": {
    "on": {
      "title": "On",
      "description": "Whether the switch is on or off.",
      "type": "boolean"
    }
  },
  "actions": {
    "toggle": {
      "title": "Toggle",
      "description": "Toggles/inverts the current 'on' state.",
      "output": {
        "title": "New 'on' state",
        "type": "boolean"
      }
    },
    "switch-on-for-duration": {
      "title": "Switch on for duration",
      "description": "Switches the switchable on for a given duration, then switches back to the previous state.",
      "input": {
        "@type": "time:Duration",
        "title": "Duration in seconds",
        "type": "integer",
        "unit": "time:seconds"
      }
    }
  }
}
```

## WoT Thing Descriptions (TDs)

### Overview

**Purpose**: Define specific Thing instances

**Spec**: https://www.w3.org/TR/wot-thing-description11/#introduction-td

**Relationship**: Kind of "object"/instance of TM being the "class"

### TD-Specific Content

**Instance identification**:
- ID of the Thing
- Security information (how to access)
- "Forms" with:
  - "href" endpoints to access Thing
  - HTTP methods to invoke
  - Protocol-specific details

**Device provision**: Device expected to provide "its own" TD
- Via HTTP endpoint running on device
- Describes all capabilities and APIs

**Intermediates**: Digital Twin platform can provide TD for device instead
- Example: Ditto can generate TD for managed Things

### Same Affordances as TMs

**Properties, Actions, Events**: Same as TM describing available capabilities

**Additional forms**: Protocol bindings to actually invoke them

### Example Thing Description

**Sample TD**:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    { "saref": "https://w3id.org/saref#" }
  ],
  "id": "urn:dev:ops:32473-WoTLamp-1234",
  "title": "MyLampThing",
  "@type": "saref:LightSwitch",
  "securityDefinitions": {
    "basic_sc": {"scheme": "basic", "in": "header"}
  },
  "security": "basic_sc",
  "properties": {
    "status": {
      "@type": "saref:OnOffState",
      "type": "string",
      "forms": [{
        "href": "https://mylamp.example.com/status"
      }]
    }
  },
  "actions": {
    "toggle": {
      "@type": "saref:ToggleCommand",
      "forms": [{
        "href": "https://mylamp.example.com/toggle"
      }]
    }
  },
  "events": {
    "overheating": {
      "data": {"type": "string"},
      "forms": [{
        "href": "https://mylamp.example.com/oh"
      }]
    }
  }
}
```

## Mapping of WoT Concepts to Ditto

### Official Mapping Documentation

**URL**: https://www.eclipse.dev/ditto/basic-wot-integration.html#mapping-of-wot-concepts-to-ditto

### Core Mappings

**Thing Model**:
- Describes model of either Ditto "Thing" or "Feature"
- Using TM "composition": Model Thing-Feature parent-child relation

**Thing Description**:
- Describes Ditto "Thing" or "Feature" instance
- Includes concrete HTTP endpoint with thingId

**Properties** → Thing "attributes" or Feature "properties" / "desiredProperties"

**Actions** → Thing "inbox messages" or Feature "inbox messages"

**Events** → Thing "outbox messages" or Feature "outbox messages"

### TM Describing Ditto Thing

**URL**: https://www.eclipse.dev/ditto/basic-wot-integration.html#thing-model-describing-a-ditto-thing

**Approach**: Use TM composition to model Thing with Features
- Top-level TM describes Thing
- Submodels describe Features

### TM Describing Ditto Feature

**URL**: https://www.eclipse.dev/ditto/basic-wot-integration.html#thing-model-describing-a-ditto-feature

**Approach**: TM describes individual Feature capabilities

## Ditto Integration: Skeleton Creation

### Overview

**Documentation**: https://www.eclipse.dev/ditto/basic-wot-integration.html#thing-skeleton-generation-upon-thing-creation

**Example**: https://www.eclipse.dev/ditto/basic-wot-integration-example.html#creating-a-new-thing-based-on-the-tm

**Feature toggle**: Deactivated by default - must be activated via environment variable

### Purpose

**Similar to Device Provisioning**: Creates empty JSON structure of target structure

**Use case**: Bootstrap new Thing with structure defined by TM

### Implementation

**Code location**:
- `WotThingSkeletonGenerator.java`
- `DefaultWotThingSkeletonGenerator.java`

**Repository**: https://github.com/eclipse-ditto/ditto/blob/master/wot/integration/src/main/java/org/eclipse/ditto/wot/integration/generator/

### Process

**Triggered when**:
1. New Thing is created
2. Thing contains a "definition"
3. Definition is a HTTP(s) URL

**Steps**:
1. Download URL resource
2. Parse as WoT TM
3. If successful, generate skeleton:
   - TM "properties" → Thing "attributes"
   - Referenced TM "submodels" → Thing "features"
   - Submodel "properties" → Feature "properties"
4. If parsing fails, create Thing without skeleton

**Result**: Thing JSON with structure from TM

## Ditto Integration: TD Generation

### Overview

**Documentation**:
- Main: https://www.eclipse.dev/ditto/basic-wot-integration.html#thing-description-generation
- For Things: https://www.eclipse.dev/ditto/basic-wot-integration.html#td-generation-for-things
- For Features: https://www.eclipse.dev/ditto/basic-wot-integration.html#td-generation-for-features

**Example**: https://www.eclipse.dev/ditto/basic-wot-integration-example.html#inspecting-the-thing-description-of-a-feature

**Feature toggle**: Deactivated by default - must be activated

### Purpose

**Automatic TD generation**: Ditto generates TD for Thing or Feature using HTTP Content-Negotiation

### Implementation

**Code location**:
- `WotThingDescriptionGenerator.java`
- `DefaultWotThingDescriptionGenerator.java`

**Repository**: https://github.com/eclipse-ditto/ditto/blob/master/wot/integration/src/main/java/org/eclipse/ditto/wot/integration/generator/

### Process

**HTTP Content-Negotiation**:
1. GET request for Thing: `GET /api/2/things/org.eclipse.ditto:my-thing-1`
2. With Accept header: `application/td+json` (IANA registered content-type for WoT TD)
3. Ditto attempts TD generation:
   - Only if TM in "definition"
   - Only if TM can be accessed and parsed
4. Downloaded TMs cached via Caffeine Cache
5. TD always calculated (not cached)

**Feature TDs**:
- Must be separately resolved
- GET `/api/2/things/org.eclipse.ditto:my-thing-1/features/my-feature`
- All features "linked" in Thing TD
- Client can follow and resolve all TD links

### Big Benefit: Introspection

**Runtime introspection possible**:
- UI can "ask" Ditto Thing for its TD
- TD contains all properties, actions, events it "understands"
- Enables dynamic UI generation
- Self-documenting Things

## Ditto API: WoT Java SDK

### Code Location

**Repository**: https://github.com/eclipse-ditto/ditto/tree/master/wot/model

**Key classes**:
- `ThingModel.java`
- `ThingDescription.java`

### Purpose

**Typed Java SDK**: Work with WoT TMs and TDs in Java

**Capabilities**:
- Builders to build TM/TD from scratch
- Serialization to JSON
- Deserialization from JSON
- Type-safe access to affordances

### Usage

**Build models programmatically**:
- Define Thing Models in Java code
- Generate Thing Descriptions
- Parse existing TMs/TDs

**Integrate in applications**:
- Process WoT models
- Generate Ditto Things based on models
- Validate model compliance

## Outlook

### WoT Thing Description 1.1 Status

**Timeline**: Final steps to become W3C "Recommendation"

**Expected**: Ready by end of 2022 (at time of session)

**Ditto response**: Mark WoT support as "non-experimental" once no changes to standard

### Adoption

**Industry interest**: Growing adoption of WoT standard

**Community**: Active WoT community in Ditto

**Collaboration**: Continued input to WoT specification

## Key Takeaways

### WoT Benefits

**Why WoT**:
- W3C standard with broad industry support
- Active development
- Ditto input shapes specification
- Best long-term bet for IoT interoperability

**Standardization**:
- Industry-wide standard
- Multiple implementations
- Interoperability focus

**Semantic richness**:
- JSON-LD for semantic context
- Links to ontologies
- References established standards

**Protocol agnostic**:
- HTTP, CoAP, MQTT, more
- Binding templates for protocols

### Ditto Integration Benefits

**Skeleton generation**:
- Bootstrap Things from models
- Consistent structure
- Similar to Device Provisioning

**TD generation**:
- Runtime introspection
- Self-documenting Things
- Dynamic UI generation

**Typed SDK**:
- Java API for WoT models
- Type-safe operations
- Easy integration

### Mapping Quality

**Excellent fit**:
- TM/TD maps naturally to Thing/Feature
- Properties, Actions, Events align well
- Composition supports Thing-Feature hierarchy

**Extension possibilities**:
- Ditto input enhanced WoT (composition)
- Continued collaboration benefits both

### Future Direction

**Non-experimental soon**:
- Once WoT TD 1.1 finalized
- Ready for production use

**Customer-driven activation**:
- Available but opt-in
- Activated upon request

**Growing interest**:
- External community
- Industry adoption

## References

- W3C WoT documentation: https://www.w3.org/WoT/documentation/
- WoT Architecture: https://www.w3.org/TR/wot-architecture/
- WoT Thing Description: https://www.w3.org/TR/wot-thing-description11/
- Ditto WoT integration: https://www.eclipse.dev/ditto/basic-wot-integration.html
- Ditto WoT example: https://www.eclipse.dev/ditto/basic-wot-integration-example.html
- Ditto examples with WoT models: https://github.com/eclipse-ditto/ditto-examples/tree/master/wot/models
- JSON-LD: https://www.w3.org/TR/json-ld11/
- WoT composition issue: https://github.com/w3c/wot-thing-description/issues/1177
