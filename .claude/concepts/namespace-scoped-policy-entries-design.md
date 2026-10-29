# Namespace-Scoped Policy Entries Design Document

**Related Issue**: [GitHub #2325 - Add optional `namespaces` field to policy entries for namespace-scoped authorization](https://github.com/eclipse-ditto/ditto/issues/2325)

**Status**: Concept
**Last Updated**: 2026-01-30

---

## TL;DR

Add an optional `namespaces` field to policy entries that restricts the entry's applicability to things within matching namespaces. This enables namespace-scoped authorization without changes to the search index structure.

### What It Does

| Capability | Description |
|------------|-------------|
| **Namespace Restriction** | Policy entries can be limited to specific namespaces |
| **Wildcard Patterns** | Support `*` suffix for matching namespace hierarchies |
| **Multi-Tenant Policies** | Single shared policy can define tenant-specific access rules |
| **Backward Compatible** | Absent field means "applies to all namespaces" |

---

## Motivation

Ditto namespaces follow reverse domain notation (e.g., `com.acme.sensors`) and are used to organize things logically. However, policy entries currently apply universally regardless of the thing's namespace. This limitation affects two common use cases:

### Multi-Tenant Scenarios

Organizations running Ditto for multiple tenants (customers, departments, projects) need to ensure that:
- Tenant A's users cannot access Tenant B's things
- A shared policy can define tenant-specific access rules
- Cross-tenant access can be explicitly granted when needed

Currently, this requires either:
- Separate policies per tenant (duplication, maintenance overhead)
- Complex subject naming conventions (error-prone)
- External authorization layers (additional infrastructure)

### Namespace-Based Thing Type Organization

Organizations often structure namespaces by thing type or domain:
- `com.acme.vehicles` - vehicle digital twins
- `com.acme.buildings` - building/facility twins
- `com.acme.sensors` - sensor devices

Different user groups may need different permissions per namespace:
- Vehicle fleet managers: full access to `com.acme.vehicles` and below
- Facility managers: full access to `com.acme.buildings` and below
- Data analysts: read-only access to all namespaces

---

## Concept

### Policy Entry with Namespace Restriction

```json
{
  "policyId": "com.acme:shared-policy",
  "entries": {
    "vehicle-admins": {
      "subjects": {
        "nginx:fleet-manager": { "type": "generated" }
      },
      "resources": {
        "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] },
        "policy:/": { "grant": ["READ"], "revoke": [] }
      },
      "namespaces": ["com.acme.vehicles", "com.acme.vehicles.*"]
    },
    "facility-admins": {
      "subjects": {
        "nginx:facility-manager": { "type": "generated" }
      },
      "resources": {
        "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] },
        "policy:/": { "grant": ["READ"], "revoke": [] }
      },
      "namespaces": ["com.acme.buildings", "com.acme.buildings.*"]
    },
    "analysts": {
      "subjects": {
        "nginx:data-analyst": { "type": "generated" }
      },
      "resources": {
        "thing:/": { "grant": ["READ"], "revoke": [] }
      }
    }
  }
}
```

The `analysts` entry has no `namespaces` field, so it applies to all namespaces (backward compatible).

### Multi-Tenant Policy Example

```json
{
  "policyId": "platform:multi-tenant-base",
  "entries": {
    "tenant-a-users": {
      "subjects": {
        "oidc:tenant-a-user-group": { "type": "generated" }
      },
      "resources": {
        "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] },
        "policy:/": { "grant": ["READ"], "revoke": [] },
        "message:/": { "grant": ["READ", "WRITE"], "revoke": [] }
      },
      "namespaces": ["com.tenant-a", "com.tenant-a.*"]
    },
    "tenant-b-users": {
      "subjects": {
        "oidc:tenant-b-user-group": { "type": "generated" }
      },
      "resources": {
        "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] },
        "policy:/": { "grant": ["READ"], "revoke": [] },
        "message:/": { "grant": ["READ", "WRITE"], "revoke": [] }
      },
      "namespaces": ["com.tenant-b", "com.tenant-b.*"]
    },
    "platform-monitoring": {
      "subjects": {
        "nginx:monitoring-service": { "type": "generated" }
      },
      "resources": {
        "thing:/": { "grant": ["READ"], "revoke": [] }
      }
    }
  }
}
```

**Behavior:**
- `com.tenant-a:device-1`: Accessible by `tenant-a-user-group` and `monitoring-service`
- `com.tenant-b:device-1`: Accessible by `tenant-b-user-group` and `monitoring-service`
- Tenant A users **cannot** access Tenant B things (and vice versa)

---

## Semantics

| `namespaces` field | Behavior |
|--------------------|----------|
| Absent | Entry applies to things in **all namespaces** (backward compatible) |
| Empty array `[]` | Same as absent - applies to all namespaces |
| Array with patterns | Entry **only applies** when thing's namespace matches at least one pattern |

### Namespace Pattern Matching

Support a simple wildcard pattern using `*` as suffix:
- `com.acme` - exact match only
- `com.acme.*` - matches everything below `com.acme` (e.g., `com.acme.vehicles`, `com.acme.vehicles.trucks.electric`) but **not** `com.acme` itself

To match both a namespace and everything below it, specify both patterns: `["com.acme", "com.acme.*"]`

Validation uses the existing `RegexPatterns.NAMESPACE_PATTERN` with `*` allowed as a trailing wildcard character.

---

## Implementation Considerations

### Search Index

The search index structure does **not** need to change. The `namespaces` filtering is applied at index write time in `EvaluatedPolicy.of()`:

1. When a thing is indexed, `EvaluatedPolicy` evaluates the policy
2. Policy entries are filtered by the thing's namespace **before** extracting subjects
3. Only subjects from applicable entries are written to the `gr` (global read) and `p` (permissions) fields
4. Existing search query logic works unchanged

This approach:
- Maintains backward compatibility
- Requires no index migration
- Keeps query performance unchanged
- Leverages existing policy change → re-index flow

### Policy Model Changes

- Add `namespaces` field to `PolicyEntry` interface and `ImmutablePolicyEntry`
- Add JSON field definition in `PolicyEntry.JsonFields`
- Reuse existing `RegexPatterns.NAMESPACE_PATTERN` for validation (with trailing `*` allowed)
- Add simple namespace matching utility (exact match or prefix match for `*` patterns)
- Update policy JSON schema documentation

### Enforcement Changes

- Runtime enforcement (`PolicyEnforcer`) filters entries by thing namespace before evaluation
- Search index evaluation (`EvaluatedPolicy`) filters entries before extracting subjects

### Policy Imports

Imported policy entries retain their `namespaces` restrictions. The importing policy cannot override or extend the namespace scope of imported entries.

---

## Design Decisions

1. **Entry-level restriction** - Namespace restrictions are on entries (not subjects or policy-level) for flexibility
2. **Simple wildcard syntax** - Single `*` suffix matches everything below, easy to understand
3. **Pre-compute at index time** - No search index structure changes, filtering happens during indexing
4. **Absent = all namespaces** - Full backward compatibility with existing policies
5. **Additive patterns** - Multiple patterns in array, entry applies if any pattern matches

---

## Open Questions

None currently - concept is ready for implementation planning.

---

## Related Documentation

- [GitHub #2304 - Limit namespaces via gateway by JWT claim](https://github.com/eclipse-ditto/ditto/issues/2304) - Complementary feature for gateway-level namespace enforcement
- [Namespace Documentation](https://eclipse.dev/ditto/basic-namespaces-and-names.html)
- [Policy Documentation](https://eclipse.dev/ditto/basic-policy.html)
- [RegexPatterns](../../base/model/src/main/java/org/eclipse/ditto/base/model/entity/id/RegexPatterns.java)
- [EvaluatedPolicy](../../thingsearch/service/src/main/java/org/eclipse/ditto/thingsearch/service/persistence/write/mapping/EvaluatedPolicy.java)
