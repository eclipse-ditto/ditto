# Policy Import Additions Design Document

**Related Issue**: [GitHub #2221 - Enhance policy imports in a way to customise the "subjects" of imported policy entries](https://github.com/eclipse-ditto/ditto/issues/2221)

**Status**: Concept
**Last Updated**: 2026-01-23

---

## TL;DR

Enhance policy imports with **`entriesAdditions`** - a mechanism to additively merge subjects and resources into imported policy entries, enabling template-based policy reuse.

### What It Does

| Capability | Description |
|------------|-------------|
| **Template Policies** | Define reusable resource structures (the "what") with empty or minimal subjects |
| **Subject Customization** | Importing policies add their own subjects (the "who") per entry |
| **Resource Extension** | Importing policies can add additional resources to imported entries |
| **Secure Merging** | Additive-only merging ensures template revokes cannot be circumvented |

---

## Motivation

Current policy imports copy entries as-is. This limits reusability because:
- Template policies must include subjects, making them specific to one use case
- No way to share resource definitions while varying access control per deployment
- Organizations cannot define "permission templates" that teams customize with their own identity groups

---

## Concept

### Template Policy (the "what")

```json
{
  "policyId": "org.eclipse.ditto:vehicle-base-policy",
  "entries": {
    "fleet-manager": {
      "subjects": {},
      "resources": {
        "thing:/": { "grant": ["READ", "WRITE"], "revoke": [] },
        "policy:/": { "grant": ["READ"], "revoke": ["WRITE"] }
      },
      "importable": "implicit"
    },
    "technician": {
      "subjects": {},
      "resources": {
        "thing:/features/diagnostics": { "grant": ["READ"], "revoke": [] }
      },
      "importable": "explicit"
    }
  }
}
```

### Importing Policy (the "who" + additional "what")

```json
{
  "policyId": "org.eclipse.ditto:truck-42-policy",
  "imports": {
    "org.eclipse.ditto:vehicle-base-policy": {
      "entries": ["technician"],
      "entriesAdditions": {
        "fleet-manager": {
          "subjects": {
            "idp:acme-fleet-managers": { "type": "ACME Fleet Manager Group" }
          }
        },
        "technician": {
          "subjects": {
            "idp:truck-technicians": { "type": "Truck Technician Group" }
          },
          "resources": {
            "thing:/features/engine": { "grant": ["READ"], "revoke": [] }
          }
        }
      }
    }
  }
}
```

### Effective Result

| Entry | Subjects | Resources |
|-------|----------|-----------|
| `fleet-manager` | `idp:acme-fleet-managers` | `thing:/ READ,WRITE` + `policy:/ READ (revoke WRITE)` |
| `technician` | `idp:truck-technicians` | `thing:/features/diagnostics READ` + `thing:/features/engine READ` |

---

## Merge Rules

### Subjects
- **Additive only**: subjects from `entriesAdditions` are added to subjects from the template
- Each subject carries its own attributes (`type`, `expiry`, `announcement`)
- No conflict possible since each subject ID is unique

### Resources
- **Additive**: new resource paths are added to the entry
- **Overlapping paths**: permission sets are merged

```
Template:  thing:/ { grant: ["READ"],         revoke: ["WRITE"] }
Addition:  thing:/ { grant: ["READ","WRITE"], revoke: [] }
───────────────────────────────────────────────────────────────
Result:    thing:/ { grant: ["READ","WRITE"], revoke: ["WRITE"] }
```

- **Security guarantee**: a `revoke` in the template cannot be removed by the importing policy

---

## Validation Behavior

| Scenario | Behavior |
|----------|----------|
| `entriesAdditions` references non-existent entry label | Silently ignored |
| `entriesAdditions` references `never`-importable entry | Silently ignored |
| `entriesAdditions` references `explicit` entry not in `entries` array | Silently ignored (entry not imported) |
| Empty addition `"operator": {}` | Valid, no effect |
| Partial addition (only `subjects` or only `resources`) | Valid |

**Rationale**: No validation because the template policy may change independently. Validating all importing policies on template changes is not feasible.

---

## Design Decisions

1. **`entries` remains an array** - No breaking change to existing API
2. **Separate `entriesAdditions` object** - Keeps backward compatibility, clear separation
3. **Per-entry granularity** - Different subjects/resources for different imported entries
4. **Additive-only merging** - Prevents security bypass, simplifies mental model
5. **No validation of references** - Graceful handling of template changes

---

## Open Questions

None currently - concept is ready for implementation planning.

---

## Related Documentation

- [Policy Documentation](https://eclipse.dev/ditto/basic-policy.html)
- [ImportableType enum](../../policies/model/src/main/java/org/eclipse/ditto/policies/model/ImportableType.java)
- [PolicyImport schema](../../documentation/src/main/resources/openapi/sources/schemas/policies/policyImport.yml)
