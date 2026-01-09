# Authentication and Authorization Deep Dive

## Overview

This deep dive explains how authentication and authorization work in Eclipse Ditto, focusing on policy-based access control.  

## Goals

Be able to analyze and solve authorization problems:
- Why does a user get a 403 (Forbidden) response?
- How to diagnose the root cause
- How to provide recommendations for fixing authorization issues

## Policies - Structure and Semantics

### Policy Basics

Policies define **who** can do **what** on **which resources**.

**Key points**:
- **Grants are case-sensitive**: Only `READ` and `WRITE` are supported
- **No 400 error for invalid grants**: Invalid grant names are silently ignored
- **Policy structure**: Organized as entries with subjects, resources, and grants

### Policy Example

```json
{
  "policyId": "my.namespace:my-policy",
  "entries": {
    "owner": {
      "subjects": {
        "{{ request:subjectId }}": {
          "type": "generated"
        }
      },
      "resources": {
        "thing:/": {
          "grant": ["READ", "WRITE"],
          "revoke": []
        },
        "policy:/": {
          "grant": ["READ", "WRITE"],
          "revoke": []
        },
        "message:/": {
          "grant": ["READ", "WRITE"],
          "revoke": []
        }
      }
    }
  }
}
```

### Subject Derivation

**Where do subjects come from?**

#### Gateway Service

Currently, derives subjects from authentication:
- JWT tokens (checked via `/whoami` endpoint)
- Subject structure from JWT follows specific pattern
- Implementation: `AuthenticationChain` in gateway service

#### Connectivity Service

**Source connections**:
- Subjects specified in Source configuration
- Defines authorization context for inbound messages

**Target connections**:
- Subjects specified in Target configuration
- Defines authorization context for outbound messages

### Policy Enforcement

**Where are policies enforced?**

Directly in the services handling commands and persisting events, e.g. for `ThingCommand`s in the "things" service.

#### Live Signals
- Enforced by `LiveSignalEnforcement.java`
- Handles real-time messaging authorization

#### Policy Commands
- Enforced by `PolicyCommandEnforcement.java`
- Controls who can modify policies themselves

#### Thing Commands
- Enforced by `ThingCommandEnforcement.java`
- Main authorization point for Thing operations

#### Connectivity Commands
- Enforced by `ConnectivityCommandEnforcement.java`
- Controls connection management authorization

## Debugging Authorization Issues

### Step-by-Step Approach

1. **Verify authenticated subject**:
   - Use `/whoami` endpoint to see current authentication context
   - Check which subjects are associated with the request

2. **Check Thing's policy**:
   - Retrieve the Thing to find its `policyId`
   - Fetch the policy using the policy ID
   - Verify policy structure and grants

3. **Validate subject matches**:
   - Compare authenticated subjects from step 1 with policy subjects
   - Check if the subject has appropriate grants (READ/WRITE)

4. **Check resource paths**:
   - Verify the policy covers the resource being accessed
   - Check for hierarchical permission inheritance

5. **Check for typos**:
   - Grants are case-sensitive (must be exactly `READ` or `WRITE`)
   - Subject IDs must match exactly

### Common Issues

- **Subject mismatch**: Authenticated subject doesn't match any policy subject
- **Insufficient grants**: Subject exists but doesn't have WRITE grant
- **Wrong resource path**: Policy doesn't cover the specific resource being modified
- **Case sensitivity**: Grants specified as "read" or "write" instead of "READ"/"WRITE"

## Action Items

- Clarify how subject structure from JWT tokens is generated in Ditto
- Document subject ID format and patterns

## References

- `policy-example.json` - Example policy structures
- Gateway authentication chain implementation
