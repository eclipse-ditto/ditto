# Code Review: Partial Access Events Feature (Issue #96)

**Branch**: `support-emitting-partial-access-events-01`
**Commits**: `4dd1de877f` through `1cc04d5391`
**Reviewer**: Claude Code
**Date**: 2026-01-12

---

## Executive Summary

This review covers the implementation of partial read access events for Eclipse Ditto, enabling subscribers with partial READ permissions to receive filtered event notifications containing only the fields they can access.

**Scope**: ~5,300 lines added, ~550 removed across 47 files

**Status**: ⚠️ **DO NOT MERGE** - Critical blockers identified

---

## Overview

### Feature Summary
Enables subscribers with partial READ permissions to receive filtered event notifications containing only the fields they can access, rather than being excluded entirely.

### Core Areas Modified
- Things service (enrichment, persistence actors)
- Connectivity (outbound mapping)
- Gateway (SSE, WebSocket routes)
- Policies model (enforcer interface)
- Protocol utilities (filtering)

### New Components
- `PartialAccessPathCalculator` - Calculates accessible paths from policy
- `AdaptablePartialAccessFilter` - Filters adaptables based on paths
- `JsonPartialAccessFilter` - Filters JSON objects
- `ThingEventEnricher` - Enriches events with partial access information
- `ReadGrant*` utility classes - Helper classes for read grant calculations

### Modified Components
- `OutboundMappingProcessor` - Major refactoring for partial access filtering
- `ThingPersistenceActor` - Integration with ThingEventEnricher
- `Enforcer` interface - New method for path calculation

---

## 🔴 CRITICAL ISSUES

### 1. Breaking Change to Public API (policies/model)

**Severity**: 🔴 BLOCKER

**Location**: `policies/model/src/main/java/org/eclipse/ditto/policies/model/enforcers/Enforcer.java`

**Issue**: New method added to `Enforcer` interface WITHOUT default implementation:

```java
Set<JsonPointer> getAccessiblePaths(ResourceKey resourceKey,
        Iterable<JsonField> jsonFields,
        AuthorizationContext authorizationContext,
        Permissions permissions);
```

**Impact**:
- `policies/model` is a **PUBLIC API** module per Ditto architecture guidelines
- External clients may have custom `Enforcer` implementations
- This breaks binary compatibility - requires all implementations to add this method
- Violates backward compatibility requirement stated in modules.md:44-48

**Evidence from modules.md:40-58**:
> **CRITICAL**: Model modules (e.g., `/things/model`, `/policies/model`) are **public API** of Ditto.
> - Changes MUST be backward compatible
> - Breaking changes require a major version bump of Ditto
> - **Avoid breaking changes at all cost**

**Recommendation**:
1. **Option A** (Preferred): Add a default implementation that throws `UnsupportedOperationException`:
   ```java
   default Set<JsonPointer> getAccessiblePaths(...) {
       throw new UnsupportedOperationException(
           "This enforcer does not support partial access path calculation");
   }
   ```
2. **Option B**: Extract to a new interface (e.g., `PartialAccessEnforcer extends Enforcer`)
3. **Option C**: Accept as breaking change and target Ditto 4.0 milestone

---

### 2. Missing Feature Toggle

**Severity**: 🔴 BLOCKER

**Location**: Entire feature implementation

**Issue**: According to CLAUDE.md and feature-toggles.md:
> "Always use Feature Toggles for new features which would change behavior of existing functionality"

This feature fundamentally changes event distribution behavior but has NO feature toggle.

**Impact**:
- Cannot disable the feature if issues arise in production
- No gradual rollout capability
- Users cannot opt-out if the feature causes problems
- Violates Ditto's feature toggle policy

**Evidence**: No config changes found for enabling/disabling partial access events

**Recommendation**:
Add a feature toggle in `things.conf`:
```hocon
ditto {
  things {
    event {
      partial-access-events {
        enabled = true
        enabled = ${?PARTIAL_ACCESS_EVENTS_ENABLED}
      }
    }
  }
}
```

Update code to check feature toggle before:
1. Calculating partial access paths in `ThingEventEnricher`
2. Filtering adaptables in `OutboundMappingProcessor`
3. Applying filters in `AdaptablePartialAccessFilter`

---

## ⚠️ MAJOR CONCERNS

### 3. Performance Impact in OutboundMappingProcessor

**Severity**: ⚠️ HIGH

**Location**: `connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/OutboundMappingProcessor.java:246-362`

**Issues**:

#### a) Per-Target Adaptable Creation

**Code** (OutboundMappingProcessor.java:272-286):
```java
private Stream<MappingOutcome<OutboundSignal.Mapped>> processWithPartialAccessPaths(...) {
    return targets.stream()
        .flatMap(target -> {
            final AuthorizationContext targetAuthContext = target.getAuthorizationContext();
            Adaptable baseAdaptable = createBaseAdaptable(source, targetAuthContext,
                outboundSignal, mappingTimer);
            // Creates separate Adaptable for EACH target
```

**Problem**:
- For N targets, creates N Adaptables instead of reusing one
- For high-throughput connections with many targets, this causes:
  - Excessive memory allocation
  - Increased GC pressure
  - Higher CPU usage

**Scenario**: Connection with 10 targets receiving 1000 events/sec = 10,000 Adaptable objects/sec

#### b) Repeated JSON Parsing

**Code** (PartialAccessPathResolver.java:114-120):
```java
final JsonObject partialAccessPathsJson =
    JsonFactory.readFrom(partialAccessPathsHeader).asObject();
```

**Problem**: Parses the partial access paths header from JSON string for every event, even when the same paths are used repeatedly.

**Impact**: CPU overhead for JSON parsing on every single event.

**Recommendation**:
1. **Cache parsed partial access paths** by header value
2. **Reuse base Adaptable** when authorization context is the same across targets
3. **Consider lazy evaluation** - only filter when target authorization differs
4. **Add performance metrics** to track overhead

**Suggested Optimization**:
```java
private final Cache<String, IndexedPartialAccessPaths> pathCache =
    CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(Duration.ofMinutes(5))
        .build();
```

---

### 4. Potential Memory Leak in Header Propagation

**Severity**: ⚠️ HIGH

**Location**: Multiple places where PARTIAL_ACCESS_PATHS header is added

**Issue**: The header contains potentially large JSON objects with all subjects and paths. This header:
1. Gets added to events in ThingEventEnricher (ThingEventEnricher.java:353)
2. Propagates through the cluster via Ditto Pub/Sub
3. May be included in external messages if not properly filtered

**Evidence**:
- AdaptablePartialAccessFilter attempts to remove it
- OutboundMappingProcessor.java:413 calls `convertToExternalHeaders` which should filter it
- However, no explicit validation exists

**Risk**: If header translation fails to remove internal headers, sensitive authorization information could leak to external systems showing:
- Which subjects have access
- Exactly which paths they can read
- Policy structure information

**Example Sensitive Data**:
```json
{
  "subjects": ["customer:user123", "customer:admin456"],
  "paths": {
    "attributes/secret": [1],
    "features/private": [0, 1]
  }
}
```

**Recommendation**:
1. **Add explicit validation** that PARTIAL_ACCESS_PATHS never appears in external messages
2. **Add test coverage** for header filtering in all outbound paths:
   - HTTP push connections
   - Kafka connections
   - MQTT connections
   - AMQP connections
3. **Add warning log** if internal header detected in external message
4. **Consider separate internal-only header mechanism** not subject to translation

**Test Example Needed**:
```java
@Test
public void internalHeadersAreNotSentToExternalSystems() {
    // Verify PARTIAL_ACCESS_PATHS is filtered from:
    // - Kafka messages
    // - HTTP POST bodies
    // - MQTT payloads
}
```

---

### 5. Complexity in OutboundMappingProcessor

**Severity**: ⚠️ MEDIUM

**Location**: OutboundMappingProcessor.java:243-362 (120 lines of complex logic)

**Issues**:
- Method `handleSignal` grew from simple mapping to complex branching logic
- Mixes multiple concerns: partial access filtering, acknowledgement filtering, header conversion
- Deep nesting (4-5 levels) makes control flow hard to follow
- Difficult to test all combinations:
  - Partial access paths present/absent
  - Targets empty/non-empty
  - Target-issued acks present/absent
  - Different event types

**Cyclomatic Complexity**: Estimated 15+ (threshold should be < 10)

**Code Smell Examples**:
```java
if (hasPartialAccessPaths && !targets.isEmpty()) {
    return processWithPartialAccessPaths(...);
} else {
    return processWithoutPartialAccessPaths(...);
}
```

Then inside `processWithoutPartialAccessPaths`:
```java
if (targets.isEmpty()) { ... }
if (!hasTargetIssuedAcks) { ... }
return targets.stream().flatMap(...);
```

**Recommendation**:
1. **Extract to separate classes** using Strategy pattern:
   ```java
   interface SignalMapper {
       Stream<MappingOutcome> mapSignal(OutboundSignal signal);
   }

   class PartialAccessSignalMapper implements SignalMapper { ... }
   class StandardSignalMapper implements SignalMapper { ... }
   ```

2. **Factory to select mapper**:
   ```java
   SignalMapper mapper = SignalMapperFactory.create(signal, targets);
   return mapper.mapSignal(signal);
   ```

3. **Reduce nesting depth** - extract helper methods
4. **Add unit tests** for each mapper independently

---

### 6. Inconsistent Empty Payload Handling

**Severity**: ⚠️ MEDIUM

**Location**:
- OutboundMappingProcessor.java:171-183
- AdaptablePartialAccessFilter.java:118-124

**Issue**: Logic for determining when to drop events with empty payloads is duplicated and slightly different:

**OutboundMappingProcessor**:
```java
private static boolean isEmptyPayloadForDrop(final Adaptable adaptable) {
    final var valueOpt = adaptable.getPayload().getValue();
    final boolean emptyObject = valueOpt
        .map(v -> v.isObject() && !v.isNull() && v.asObject().isEmpty())
        .orElse(true);
    final boolean emptyPrimitive = valueOpt.isEmpty();
    return emptyObject || emptyPrimitive;
}
```

**AdaptablePartialAccessFilter**:
```java
private static Adaptable createEmptyPayloadAdaptable(final Adaptable adaptable) {
    return ProtocolFactory.newAdaptableBuilder(adaptable)
        .withPayload(Payload.newBuilder(adaptable.getPayload())
            .withValue(JsonFactory.newObject())
            .build())
        .build();
}
```

**Problem**:
- Different representations of "empty"
- One checks for empty, another creates `JsonFactory.newObject()`
- Unclear semantics: Is `JsonObject.empty()` the same as `Optional.empty()`?
- Potential bugs if assumptions differ

**Recommendation**:
1. Centralize empty payload handling in one place
2. Define clear semantics: What does "empty" mean?
3. Add documentation and examples
4. Create utility class: `EmptyPayloadDetector` with clear contract

---

## ⚡ MINOR ISSUES

### 7. Unrelated Config Change

**Severity**: ⚡ LOW

**Location**: `things/service/src/main/resources/things-dev.conf`

**Issue**:
```diff
-        remove-empty-objects-after-patch-condition-filtering = true
+        remove-empty-objects-after-patch-condition-filtering = false
```

**Problem**:
- This change appears unrelated to partial access events
- No explanation in commit message
- Should be in a separate commit/PR
- May affect existing merge-patch behavior

**Recommendation**:
- Revert this change
- OR explain the relationship to partial access events
- OR move to separate PR with proper justification

---

### 8. Dead Code

**Severity**: ⚡ LOW

**Location**: OutboundMappingProcessor.java:295-310

**Issue**: Method `shouldFilterForTarget` has complex logic but is NEVER CALLED:

```java
/**
 * Determines if empty filtered payloads should result in dropping the event for a target.
 * This only applies to Thing events with partial access information present.
 */
private static boolean shouldFilterForTarget(
        final Adaptable adaptable,
        final DittoHeaders rootHeaders) {
    // 15 lines of logic...
}
```

**Problem**:
- Increases code complexity
- Confuses future maintainers
- May indicate incomplete implementation

**Recommendation**:
- Remove if not needed
- OR implement the caller if this was intended to be used
- OR document why it's kept for future use

---

### 9. Potential NPE in PartialAccessPathCalculator

**Severity**: ⚡ LOW

**Location**: `things/service/src/main/java/org/eclipse/ditto/things/service/utils/PartialAccessPathCalculator.java:296`

**Issue**:
```java
if (subjectIndex == null) {
    // Subject not in index (should not happen, but defensive check)
    continue;
}
```

**Problem**:
- Defensive check suggests uncertainty about invariants
- Comment says "should not happen" but doesn't explain why
- If it truly cannot happen, the check is noise
- If it can happen, the handling (silent continue) may hide bugs

**Recommendation**:
- **Option A**: Prove it cannot happen and remove the check
- **Option B**: Add logging if it does happen:
  ```java
  if (subjectIndex == null) {
      LOGGER.error("Subject {} not found in index, this is a bug", subjectId);
      throw new IllegalStateException("Subject not in index: " + subjectId);
  }
  ```
- **Option C**: Document the scenario where this can occur

---

### 10. Inconsistent Parameter Ordering

**Severity**: ⚡ LOW

**Location**: ThingEventEnricher method signature change

**Before**:
```java
enrichWithPredefinedExtraFields(configs, thingId, thing, policyId, signal)
```

**After**:
```java
enrichWithPredefinedExtraFields(thingId, thing, policyId, configs, signal)
```

**Impact**:
- Not a breaking change (internal class)
- But inconsistent with previous pattern where configs came first
- May confuse developers familiar with old signature

**Recommendation**:
- Keep consistent ordering across the codebase
- Usually put configs/dependencies first, then data
- Document the reasoning for the change

---

## ✅ POSITIVE ASPECTS

### 1. Excellent Test Coverage ⭐

**Statistics**:
- 13 test files modified/added
- ~2,000 lines of test code added

**Comprehensive Unit Tests**:
- `AdaptablePartialAccessFilterTest` - 738 lines
  - Tests filtering for different event types
  - Tests with/without partial access paths
  - Tests edge cases (empty payloads, null values)
- `PartialAccessPathCalculatorTest` - 265 lines
  - Tests path calculation from policies
  - Tests with multiple subjects
  - Tests with nested paths
- `ReadGrantCollectorTest` - 229 lines
  - Tests read grant collection
  - Tests with various policy configurations
- `PartialAccessPathResolverTest` - 222 lines
  - Tests path resolution logic
  - Tests indexed format parsing

**Integration Tests**:
- `OutboundMappingProcessorTest` - 419 lines added
  - Tests end-to-end filtering
  - Tests with real connections
- `ThingsSseRouteBuilderTest` - 137 lines added
  - Tests SSE event streaming with filtering

**Quality**: Tests are well-structured with clear arrange-act-assert pattern.

---

### 2. Clean Separation of Concerns ⭐

The implementation follows good architectural principles:

**Single Responsibility**:
- `PartialAccessPathCalculator` - ONLY calculates paths from policy
- `AdaptablePartialAccessFilter` - ONLY filters adaptables
- `JsonPartialAccessFilter` - ONLY filters JSON objects
- `PartialAccessPathResolver` - ONLY resolves accessible paths for subjects

**Benefits**:
- Each class has a clear, testable purpose
- Easy to understand individual components
- Changes to one component don't affect others
- Good for maintenance and debugging

**Example**:
```java
// Clear pipeline of responsibilities:
1. PartialAccessPathCalculator.calculatePartialAccessPaths()
   → Map<String, List<JsonPointer>>

2. PartialAccessPathCalculator.toIndexedJsonObject()
   → JsonObject (indexed format)

3. PartialAccessPathResolver.resolveAccessiblePathsFromHeader()
   → AccessiblePathsResult

4. AdaptablePartialAccessFilter.filterAdaptableWithResult()
   → Filtered Adaptable
```

---

### 3. Indexed Format Optimization ⭐

The indexed JSON format for partial access paths is a clever optimization:

**Problem**: Naive format would be large:
```json
{
  "subject1": ["path1", "path2", "path3"],
  "subject2": ["path1", "path2"],
  "subject3": ["path1"]
}
```

**Solution**: Indexed format:
```json
{
  "subjects": ["subject1", "subject2", "subject3"],
  "paths": {
    "path1": [0, 1, 2],
    "path2": [0, 1],
    "path3": [0]
  }
}
```

**Benefits**:
- Reduces header size when multiple subjects share paths
- Inverts the relationship: paths → subjects instead of subjects → paths
- More efficient for common case: many subjects, few paths
- Reduces cluster bandwidth usage

**Measurements** (example):
- 10 subjects with same 5 paths
- Naive: ~500 bytes
- Indexed: ~200 bytes
- **Savings: 60%**

---

### 4. Proper Immutability ⭐

All new classes follow Ditto's immutability patterns:

**Final Fields**:
```java
public final class PartialAccessPathCalculator {
    private static final JsonPointer ROOT_RESOURCE_POINTER = JsonPointer.empty();
    private static final Permissions READ_PERMISSIONS = Permissions.newInstance(Permission.READ);
    // All fields final, all methods static
}
```

**Final Parameters**:
```java
public static Map<String, List<JsonPointer>> calculatePartialAccessPaths(
        @Nullable final Thing thing,
        final PolicyEnforcer policyEnforcer) {
    // Parameters are final
}
```

**Immutable Collections**:
```java
private static Set<AuthorizationSubject> filterSubjectsWithRestrictedAccess(...) {
    final Set<AuthorizationSubject> result = new LinkedHashSet<>(...);
    // Returns unmodifiable view or creates new set
    return Set.copyOf(result);
}
```

**Alignment with code-patterns.md**:
- ✅ Uses `final` keyword everywhere
- ✅ Immutable collections
- ✅ No mutable state
- ✅ Functional style with streams

---

### 5. License Headers ⭐

All new files have correct Eclipse Public License 2.0 headers:

```java
/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
```

**Compliance**: ✅ All 20+ new files have proper headers

---

### 6. Good Documentation ⭐

**Javadoc Quality**:
- Classes have comprehensive class-level documentation
- Methods have parameter descriptions
- Complex logic is explained with examples

**Example** (PartialAccessPathCalculator.java:197-222):
```java
/**
 * Converts a map of subject IDs to accessible paths into an indexed JSON object format.
 * <p>
 * This format uses integer indices to reference subjects, significantly reducing header size
 * when there are many subjects or paths. Instead of repeating subject IDs multiple times,
 * each subject is assigned an index and paths reference these indices.
 * </p>
 * <p>
 * Example transformation:
 * </p>
 * <pre>{@code
 * // Input:
 * {
 *   "subject1": ["/attributes/foo", "/features/A/properties/baz"],
 *   "subject2": ["/attributes/foo"]
 * }
 *
 * // Output:
 * {
 *   "subjects": ["subject1", "subject2"],
 *   "paths": {
 *     "attributes/foo": [0, 1],
 *     "features/A/properties/baz": [0]
 *   }
 * }
 * }</pre>
 */
```

---

## 📋 RECOMMENDATIONS SUMMARY

### Must Fix (Before Merge) 🔴

| # | Issue | Severity | Location | Effort |
|---|-------|----------|----------|--------|
| 1 | Add default implementation to `Enforcer.getAccessiblePaths()` | BLOCKER | policies/model | 2h |
| 2 | Add feature toggle for partial access events | BLOCKER | things.conf + code | 4h |
| 3 | Remove or explain unrelated config change | LOW | things-dev.conf | 1h |
| 4 | Remove dead code (`shouldFilterForTarget`) | LOW | OutboundMappingProcessor | 0.5h |

**Total Effort**: ~7.5 hours

---

### Should Fix (High Priority) ⚠️

| # | Issue | Severity | Effort |
|---|-------|----------|--------|
| 5 | Optimize OutboundMappingProcessor (reduce per-target overhead) | HIGH | 8h |
| 6 | Add header leak prevention tests | HIGH | 4h |
| 7 | Refactor OutboundMappingProcessor complexity | MEDIUM | 12h |
| 8 | Centralize empty payload handling | MEDIUM | 4h |

**Total Effort**: ~28 hours

---

### Consider (Lower Priority) 💡

| # | Issue | Effort |
|---|-------|--------|
| 9 | Cache parsed partial access paths | 4h |
| 10 | Document performance implications | 2h |
| 11 | Add metrics (filtering rate, header sizes) | 6h |
| 12 | Consider batch optimization | 12h |

**Total Effort**: ~24 hours

---

## 🎯 ARCHITECTURE COMPLIANCE

### Alignment with Ditto Patterns ✅

**Signal Pattern**: ✅ COMPLIANT
- Events remain immutable
- Filtering happens at edges, not during persistence
- No modification of event sourcing journal

**Event Sourcing**: ✅ COMPLIANT
- Events stored before filtering
- Complete events in journal
- Filtering only affects distribution, not storage

**Authorization at Edges**: ✅ COMPLIANT per Authentication-Authorization.md
- Filtering in Gateway (SSE, WebSocket)
- Filtering in Connectivity (outbound mapping)
- NOT in persistence actors (ThingPersistenceActor only calculates paths)

**Pub/Sub Pattern**: ✅ COMPLIANT per Ditto-PubSub.md
- Uses existing Ditto Pub/Sub infrastructure
- Adds partial access paths to headers for routing
- No changes to pub/sub mechanics

**Enrichment at Edges**: ✅ COMPLIANT per Enrichment.md
- Path calculation happens in ThingEventEnricher (edge concern)
- Filtering happens in Gateway/Connectivity (edges)
- Performance impact is at edges, not in persistence layer

---

### Concerns ⚠️

**Breaking Change to Public API**: ⚠️ VIOLATION
- `Enforcer` interface is public API
- No default implementation provided
- Violates modules.md:44-48 backward compatibility requirement

**No Feature Toggle**: ⚠️ VIOLATION
- Violates feature-toggles.md policy
- No way to disable feature in production
- No gradual rollout capability

**Performance Impact**: ⚠️ CONCERN
- Per-target overhead not measured
- JSON parsing per event not optimized
- High-throughput scenarios not validated

---

## 📊 METRICS & STATISTICS

### Code Changes
- **Files Modified**: 47
- **Lines Added**: ~5,300
- **Lines Removed**: ~550
- **Net Change**: +4,750 lines

### Test Coverage
- **Test Files**: 13
- **Test Lines Added**: ~2,000
- **Test Files Created**: 7 new test classes

### Module Impact
- **Core Modules**: 4 (things, connectivity, gateway, policies)
- **Utility Modules**: 2 (internal/utils/protocol)
- **Model Modules**: 1 (policies/model) ⚠️ Public API

### Complexity
- **New Classes**: 15+
- **New Methods in Existing Classes**: 20+
- **Cyclomatic Complexity**:
  - `OutboundMappingProcessor.handleSignal()`: ~15 (target: <10)
  - `AdaptablePartialAccessFilter.filterAdaptableWithResult()`: ~8
  - `PartialAccessPathCalculator.calculatePartialAccessPaths()`: ~6

---

## 🔍 TESTING RECOMMENDATIONS

### Required Additional Tests

1. **Performance Tests**:
   ```java
   @Test
   public void testHighThroughputWithManyTargets() {
       // 1000 events/sec with 10 targets
       // Measure: CPU, memory, latency
   }
   ```

2. **Header Leak Tests**:
   ```java
   @Test
   public void partialAccessPathsHeaderNotInExternalMessages() {
       // Verify for all connection types
   }
   ```

3. **Feature Toggle Tests**:
   ```java
   @Test
   public void partialAccessEventsDisabledWhenToggleOff() {
       // Verify feature respects toggle
   }
   ```

4. **Integration Tests**:
   - End-to-end with real MongoDB
   - Multiple connections with different auth contexts
   - Mixed partial and full access subscribers

---

## 🚀 ROLLOUT STRATEGY (Post-Fix)

### Phase 1: Feature Flag Development
- Add feature toggle (disabled by default)
- Add metrics and monitoring
- Duration: 1 sprint

### Phase 2: Internal Testing
- Enable for dev/test environments
- Performance testing with realistic loads
- Security testing (header leakage)
- Duration: 2 weeks

### Phase 3: Canary Deployment
- Enable for 5% of production traffic
- Monitor metrics closely
- Gradual increase to 50%
- Duration: 2 weeks

### Phase 4: Full Rollout
- Enable for 100% of traffic
- Keep feature flag for emergency disable
- Remove flag after 1 stable release

---

## CONCLUSION

This is a **well-architected feature** with **excellent test coverage** that addresses a real need documented in the TwinEvents deep-dive (lines 178-186: "Planned feature: Partial event support").

### Strengths
- ✅ Clean separation of concerns
- ✅ Comprehensive test coverage
- ✅ Clever optimization (indexed format)
- ✅ Proper immutability patterns
- ✅ Good documentation

### Critical Blockers
- 🔴 Breaking change to public API
- 🔴 Missing feature toggle

### Major Concerns
- ⚠️ Performance impact not validated
- ⚠️ Complexity in OutboundMappingProcessor
- ⚠️ Potential header leakage

---

## FINAL RECOMMENDATION

**Status**: ⚠️ **DO NOT MERGE**

**Required Actions**:
1. ✅ Fix breaking API change (add default implementation)
2. ✅ Add feature toggle
3. ⚠️ Performance testing with realistic workloads
4. ⚠️ Security review for header leakage
5. 💡 Refactor OutboundMappingProcessor complexity

**Estimated Time to Production-Ready**:
- Critical fixes: 1-2 days
- Performance optimization: 1 week
- Testing and validation: 1-2 weeks
- **Total**: 3-4 weeks

**Risk Level**: MEDIUM-HIGH
- Feature is sound architecturally
- Implementation needs optimization and safety improvements
- Worth the effort - addresses important use case

---

**Reviewed By**: Claude Code
**Date**: 2026-01-12
**Ditto Branch**: `support-emitting-partial-access-events-01`
**Based On**: master branch + architecture deep-dives + code patterns
