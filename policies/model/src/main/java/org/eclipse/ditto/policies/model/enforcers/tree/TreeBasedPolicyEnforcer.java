/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.SubjectClassification;

/**
 * Holds Algorithms to create a policy tree and to perform different policy checks on this tree.
 */
public final class TreeBasedPolicyEnforcer implements Enforcer {

    private static final String ROOT_RESOURCE = "/";
    private static final JsonPointer ROOT_RESOURCE_POINTER = JsonFactory.newPointer(ROOT_RESOURCE);

    /**
     * Default best-effort upper bound on each authorization-verdict memo (see below), used by the no-size
     * {@link #createInstance(Iterable)} factory. Operators can override it (see
     * {@link #createInstance(Iterable, int)}); a value {@code <= 0} disables memoization entirely (no maps are
     * allocated). Keeps memory bounded should a caller ever produce pathologically many distinct keys (e.g.
     * huge numbers of distinct resources or subject-sets). Above the cap, verdicts are computed without being
     * cached — correctness is unaffected, only the fast path is skipped. Mirrors the bounded best-effort caches
     * used by {@code PolicyEnforcer}.
     */
    private static final int DEFAULT_MAX_MEMO_SIZE = 10_000;

    /**
     * Maps subject ID to {@link org.eclipse.ditto.policies.model.enforcers.tree.SubjectNode} whose children are {@link org.eclipse.ditto.policies.model.enforcers.tree.ResourceNode} for which the subject is granted
     * or revoked access. The child-set of each {@link org.eclipse.ditto.policies.model.enforcers.tree.SubjectNode} is effectively a map from resources to permissions.
     */
    private final Map<String, PolicyTreeNode> tree;

    // Per-instance memos of authorization verdicts. TreeBasedPolicyEnforcer is effectively immutable after
    // createInstance: the tree is built once during construction and only ever read (never mutated) by
    // visitTree afterwards. A policy change produces a brand-new instance via
    // PolicyEnforcers.defaultEvaluator, so - exactly like the classifySubjects(resource, READ) memo on
    // PolicyEnforcer - each memo's lifetime equals the instance's and needs no explicit invalidation.
    // Every memoized method walks the whole policy tree per call (visitTree), which production JFR showed to
    // be a dominant things-service CPU hotspot; the (resource, subjects, permissions) tuples recur heavily,
    // so a hash lookup replaces the repeated full-tree traversal. ConcurrentHashMap because enforcer
    // instances are shared and hit from multiple threads concurrently.
    // When memoization is disabled (maxMemoSize <= 0) these are left null and NOT allocated: every method then
    // takes the direct visitTree path, behaving exactly like a non-memoizing enforcer with zero extra memory.
    private final int maxMemoSize;
    private final boolean memoizationEnabled;
    @Nullable
    private final Map<PermissionCheckKey, Boolean> permissionCheckMemo;
    @Nullable
    private final Map<SubjectsKey, EffectedSubjects> effectedSubjectsMemo;
    @Nullable
    private final Map<SubjectsKey, Set<AuthorizationSubject>> partialSubjectsMemo;

    private TreeBasedPolicyEnforcer(final Map<String, PolicyTreeNode> tree, final int maxMemoSize) {
        this.tree = tree;
        this.maxMemoSize = maxMemoSize;
        this.memoizationEnabled = maxMemoSize > 0;
        if (memoizationEnabled) {
            permissionCheckMemo = new ConcurrentHashMap<>();
            effectedSubjectsMemo = new ConcurrentHashMap<>();
            partialSubjectsMemo = new ConcurrentHashMap<>();
        } else {
            permissionCheckMemo = null;
            effectedSubjectsMemo = null;
            partialSubjectsMemo = null;
        }
    }

    /**
     * Creates a new policy tree for execution of policy checks, using the {@link #DEFAULT_MAX_MEMO_SIZE default}
     * authorization-verdict memo bound.
     *
     * @param policyEntries the policy entries to create a tree for
     * @return the generated {@code TreeBasedPolicyEnforcer}
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static TreeBasedPolicyEnforcer createInstance(final Iterable<PolicyEntry> policyEntries) {
        return createInstance(policyEntries, DEFAULT_MAX_MEMO_SIZE);
    }

    /**
     * Creates a new policy tree for execution of policy checks, bounding each authorization-verdict memo at
     * {@code maxMemoSize} entries.
     *
     * @param policyEntries the policy entries to create a tree for
     * @param maxMemoSize the per-memo best-effort upper bound; a value {@code <= 0} disables memoization
     * entirely (no {@link ConcurrentHashMap}s are allocated and every check takes the direct tree-walk path).
     * @return the generated {@code TreeBasedPolicyEnforcer}
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static TreeBasedPolicyEnforcer createInstance(final Iterable<PolicyEntry> policyEntries,
            final int maxMemoSize) {
        checkNotNull(policyEntries, "policyEntries");
        final Map<String, PolicyTreeNode> tree = new HashMap<>();

        policyEntries.forEach(policyEntry -> {

            final Subjects subjects = policyEntry.getSubjects();
            subjects.forEach(subject -> {
                final SubjectId subjectId = subject.getId();
                final String subjectIdString = subjectId.toString();
                final PolicyTreeNode parentNode = tree.computeIfAbsent(subjectIdString, SubjectNode::of);

                final Resources resources = policyEntry.getResources();
                resources.forEach(resource -> {
                    final PolicyTreeNode rootChild = parentNode.computeIfAbsent(resource.getType(), t -> {
                        final Set<String> emptySet = Collections.emptySet();
                        return ResourceNode.of(parentNode, t, EffectedPermissions.newInstance(emptySet, emptySet));
                    });
                    addResourceSubTree((ResourceNode) rootChild, resource, resource.getPath());
                });
            });
        });

        return new TreeBasedPolicyEnforcer(tree, maxMemoSize);
    }

    private static void addResourceSubTree(final ResourceNode parentNode, final Resource resource,
            final JsonPointer path) {

        if (path.getLevelCount() == 1 || ROOT_RESOURCE.equals(path.toString())) {
            final String usedPath = ROOT_RESOURCE.equals(path.toString()) ? ROOT_RESOURCE : path.getRoot()
                    .map(JsonKey::toString)
                    .orElseThrow(() -> new NullPointerException("Path did not contain a root!"));

            if (usedPath.equals(ROOT_RESOURCE)) {
                parentNode.getParent().ifPresent(p -> mergePermissions(resource, parentNode));
            } else if (!parentNode.getChild(usedPath).isPresent()) {
                parentNode.addChild(ResourceNode.of(parentNode, usedPath, resource.getEffectedPermissions()));
            } else {
                final ResourceNode existingChild = parentNode.getChild(usedPath)
                        .map(ResourceNode.class::cast)
                        .orElseThrow(() -> {
                            final String msgPattern = "Parent node did not contain a child for path <{}>!";
                            return new NullPointerException(MessageFormat.format(msgPattern, usedPath));
                        });

                mergePermissions(resource, existingChild);
            }
        } else {
            final String pathRootAsString = path.getRoot()
                    .map(JsonKey::toString)
                    .orElse("");
            final ResourceNode node = (ResourceNode) parentNode.getChild(pathRootAsString).orElseGet(() -> {
                final PolicyTreeNode newChild = ResourceNode.of(parentNode, pathRootAsString);
                parentNode.addChild(newChild);
                return newChild;
            });
            addResourceSubTree(node, resource, path.nextLevel());
        }
    }

    private static void mergePermissions(final Resource resource, final ResourceNode existingChild) {
        final EffectedPermissions existingChildPermissions = existingChild.getPermissions();
        final Collection<String> mergedGrantedPermissions =
                new HashSet<>(existingChildPermissions.getGrantedPermissions());
        final Collection<String> mergedRevokedPermissions =
                new HashSet<>(existingChildPermissions.getRevokedPermissions());

        if (!resource.getEffectedPermissions().getRevokedPermissions().isEmpty()) {
            mergedRevokedPermissions.addAll(resource.getEffectedPermissions().getRevokedPermissions());
        }
        if (!resource.getEffectedPermissions().getGrantedPermissions().isEmpty()) {
            mergedGrantedPermissions.addAll(resource.getEffectedPermissions().getGrantedPermissions());
        }

        existingChild.setPermissions(
                EffectedPermissions.newInstance(mergedGrantedPermissions, mergedRevokedPermissions));
    }

    @Override
    public boolean hasUnrestrictedPermissions(final ResourceKey resourceKey,
            final AuthorizationContext authorizationContext, final Permissions permissions) {

        checkPermissions(permissions);
        // resourcePointer/authSubjectIds are computed eagerly (cheap, and preserving the original
        // null-argument check ordering); only the expensive visitTree call is deferred into the memo supplier.
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        final Set<String> authSubjectIds = getAuthorizationSubjectIds(authorizationContext);
        if (!memoizationEnabled) {
            return visitTree(new CheckUnrestrictedPermissionsVisitor(resourcePointer, authSubjectIds, permissions));
        }
        return memoize(permissionCheckMemo,
                new PermissionCheckKey(resourceKey, authSubjectIds, permissions, false),
                () -> visitTree(new CheckUnrestrictedPermissionsVisitor(resourcePointer, authSubjectIds, permissions)));
    }

    private static void checkPermissions(final Permissions permissions) {
        checkNotNull(permissions, "permissions to check");
    }

    private static JsonPointer createAbsoluteResourcePointer(final ResourceKey resourceKey) {
        return JsonFactory.newPointer(resourceKey.getResourceType()).append(resourceKey.getResourcePath());
    }

    private static Set<String> getAuthorizationSubjectIds(final AuthorizationContext authorizationContext) {
        checkNotNull(authorizationContext, "Authorization Context");

        return authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet());
    }

    private <T> T visitTree(final Visitor<T> visitor) {
        tree.values().forEach(policyTreeNode -> policyTreeNode.accept(visitor));
        return visitor.get();
    }

    /**
     * Returns the memoized value for {@code key}, computing and caching it (best-effort, up to
     * {@link #maxMemoSize} entries) on a miss. All memoized suppliers return non-null values, so a
     * {@code null} lookup reliably means "absent". Thread-safe: {@code memo} is a {@link ConcurrentHashMap}
     * and the supplier only reads the immutable policy tree, never re-entering the memo. Defensively
     * short-circuits to the supplier if {@code memo} is {@code null} (memoization disabled); callers already
     * guard on {@link #memoizationEnabled}, so this is belt-and-suspenders.
     */
    private <K, V> V memoize(@Nullable final Map<K, V> memo, final K key, final Supplier<V> valueSupplier) {
        if (memo == null) {
            return valueSupplier.get();
        }
        final V cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        final V computed = valueSupplier.get();
        if (memo.size() < maxMemoSize) {
            memo.putIfAbsent(key, computed);
        }
        return computed;
    }

    /**
     * Package-private test hook: whether this instance memoizes authorization verdicts. {@code false} means the
     * verdict memos were not allocated (constructed with {@code maxMemoSize <= 0}) and every check walks the
     * tree directly.
     *
     * @return {@code true} if memoization is enabled.
     */
    boolean isMemoizationEnabled() {
        return memoizationEnabled;
    }

    @Override
    public EffectedSubjects getSubjectsWithPermission(final ResourceKey resourceKey, final Permissions permissions) {
        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        if (!memoizationEnabled) {
            return visitTree(new CollectEffectedSubjectsVisitor(resourcePointer, permissions));
        }
        // EffectedSubjects (DefaultEffectedSubjects) is @Immutable with unmodifiable internal sets, so the
        // cached instance can be shared across callers directly.
        return memoize(effectedSubjectsMemo, new SubjectsKey(resourceKey, permissions),
                () -> visitTree(new CollectEffectedSubjectsVisitor(resourcePointer, permissions)));
    }

    private static void checkResourceKey(final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "resource key");
    }

    @Override
    public Set<AuthorizationSubject> getSubjectsWithPartialPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        if (!memoizationEnabled) {
            return visitTree(new CollectPartialGrantedSubjectsVisitor(resourcePointer, permissions));
        }
        // The visitor returns a fresh mutable HashSet; memoize an immutable snapshot but hand each caller a
        // fresh mutable copy, preserving the original contract (callers receive a private, mutable set).
        final Set<AuthorizationSubject> cached = memoize(partialSubjectsMemo,
                new SubjectsKey(resourceKey, permissions),
                () -> Collections.unmodifiableSet(new HashSet<>(
                        visitTree(new CollectPartialGrantedSubjectsVisitor(resourcePointer, permissions)))));
        return new HashSet<>(cached);
    }

    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey, final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final Set<String> authSubjectIds = getAuthorizationSubjectIds(authorizationContext);
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        if (!memoizationEnabled) {
            return visitTree(new CheckPartialPermissionsVisitor(resourcePointer, authSubjectIds, permissions));
        }
        return memoize(permissionCheckMemo,
                new PermissionCheckKey(resourceKey, authSubjectIds, permissions, true),
                () -> visitTree(new CheckPartialPermissionsVisitor(resourcePointer, authSubjectIds, permissions)));
    }

    @Override
    public Set<AuthorizationSubject> getSubjectsWithUnrestrictedPermission(final ResourceKey resourceKey,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        return visitTree(new CollectUnrestrictedSubjectsVisitor(resourcePointer, permissions));
    }

    @Override
    public SubjectClassification classifySubjects(final ResourceKey resourceKey, final Permissions permissions) {
        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        return visitTree(new ClassifySubjectsVisitor(resourcePointer, permissions));
    }

    @Override
    public JsonObject buildJsonView(
            final ResourceKey resourceKey,
            final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkNotNull(jsonFields, "JSON fields");
        checkPermissions(permissions);
        final Collection<String> authorizationSubjectIds = getAuthorizationSubjectIds(authorizationContext);

        final EffectedResources effectedResources = getGrantedAndRevokedSubResource(
                ROOT_RESOURCE_POINTER, resourceKey.getResourceType(), authorizationSubjectIds,
                permissions);

        if (jsonFields instanceof JsonObject && ((JsonObject) jsonFields).isNull()) {
            return JsonFactory.nullObject();
        }

        final List<PointerAndValue> flatPointers = new ArrayList<>();
        jsonFields.forEach(jsonField -> collectFlatPointers(jsonField.getKey().asPointer(), jsonField, flatPointers));
        final Set<JsonPointer> grantedResources = extractJsonPointers(effectedResources.getGrantedResources());
        final Set<JsonPointer> revokedResources = extractJsonPointers(effectedResources.getRevokedResources());

        final JsonPointer resourcePath = resourceKey.getResourcePath();
        // When the resource path is empty (the thing-root case), `resourcePath.append(pv.pointer)`
        // returns an equivalent pointer but allocates a fresh ImmutableJsonPointer and a new
        // PointerAndValue wrapper for every flat pointer. Skip the per-element rebuild.
        final List<PointerAndValue> prefixedPointers = resourcePath.isEmpty()
                ? flatPointers
                : flatPointers.stream()
                        .map(pv -> new PointerAndValue(resourcePath.append(pv.pointer), pv.value))
                        .collect(Collectors.toList());
        return filterEntries(prefixedPointers, grantedResources, revokedResources, resourcePath);
    }

    @Override
    public Set<JsonPointer> getAccessiblePaths(
            final ResourceKey resourceKey,
            final Iterable<JsonField> jsonFields,
            final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkNotNull(jsonFields, "JSON fields");
        checkPermissions(permissions);
        final Collection<String> authorizationSubjectIds = getAuthorizationSubjectIds(authorizationContext);

        final EffectedResources effectedResources = getGrantedAndRevokedSubResource(
                ROOT_RESOURCE_POINTER, resourceKey.getResourceType(), authorizationSubjectIds,
                permissions);

        if (jsonFields instanceof JsonObject && ((JsonObject) jsonFields).isNull()) {
            return Collections.emptySet();
        }

        final List<PointerAndValue> flatPointers = new ArrayList<>();
        jsonFields.forEach(jsonField -> collectFlatPointers(jsonField.getKey().asPointer(), jsonField, flatPointers));
        final Set<JsonPointer> grantedResources = extractJsonPointers(effectedResources.getGrantedResources());
        final Set<JsonPointer> revokedResources = extractJsonPointers(effectedResources.getRevokedResources());

        final JsonPointer resourcePath = resourceKey.getResourcePath();
        // When the resource path is empty (the thing-root case), `resourcePath.append(pv.pointer)`
        // returns an equivalent pointer but allocates a fresh ImmutableJsonPointer and a new
        // PointerAndValue wrapper for every flat pointer. Skip the per-element rebuild.
        final List<PointerAndValue> prefixedPointers = resourcePath.isEmpty()
                ? flatPointers
                : flatPointers.stream()
                        .map(pv -> new PointerAndValue(resourcePath.append(pv.pointer), pv.value))
                        .collect(Collectors.toList());
        return extractAccessiblePaths(prefixedPointers, grantedResources, revokedResources, resourcePath);
    }

    @Override
    public Map<AuthorizationSubject, Set<JsonPointer>> getAccessiblePathsForSubjects(
            final ResourceKey resourceKey, final Iterable<JsonField> jsonFields,
            final Set<AuthorizationSubject> authorizationSubjects, final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkNotNull(jsonFields, "JSON fields");
        checkPermissions(permissions);

        if (jsonFields instanceof JsonObject && ((JsonObject) jsonFields).isNull()) {
            return Collections.emptyMap();
        }

        // 1. Flatten once
        final List<PointerAndValue> flatPointers = new ArrayList<>();
        jsonFields.forEach(jf -> collectFlatPointers(jf.getKey().asPointer(), jf, flatPointers));
        final JsonPointer resourcePath = resourceKey.getResourcePath();
        // When the resource path is empty (the thing-root case), `resourcePath.append(pv.pointer)`
        // returns an equivalent pointer but allocates a fresh ImmutableJsonPointer and a new
        // PointerAndValue wrapper for every flat pointer. Skip the per-element rebuild.
        final List<PointerAndValue> prefixedPointers = resourcePath.isEmpty()
                ? flatPointers
                : flatPointers.stream()
                        .map(pv -> new PointerAndValue(resourcePath.append(pv.pointer), pv.value))
                        .collect(Collectors.toList());

        // 2. Per-subject: tree walk + filter (reusing prefixedPointers)
        final Map<AuthorizationSubject, Set<JsonPointer>> result = new HashMap<>();
        for (final AuthorizationSubject subject : authorizationSubjects) {
            final Collection<String> subjectIds = Collections.singleton(subject.getId());
            final EffectedResources effectedResources = getGrantedAndRevokedSubResource(
                    ROOT_RESOURCE_POINTER, resourceKey.getResourceType(), subjectIds, permissions);

            final Set<JsonPointer> grantedResources = extractJsonPointers(effectedResources.getGrantedResources());
            final Set<JsonPointer> revokedResources = extractJsonPointers(effectedResources.getRevokedResources());

            final Set<JsonPointer> paths = extractAccessiblePaths(
                    prefixedPointers, grantedResources, revokedResources, resourcePath);
            if (!paths.isEmpty()) {
                result.put(subject, paths);
            }
        }
        return result;
    }

    private static Set<JsonPointer> extractJsonPointers(final Collection<PointerAndPermission> resources) {
        return resources.stream()
                .map(pointerAndPermission -> pointerAndPermission.pointer)
                .collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "tree=" + tree + "]";
    }

    private static List<PointerAndValue> collectFlatPointers(final JsonPointer createdPointer, final JsonField field,
            final List<PointerAndValue> flattenedFields) {

        final JsonValue fieldValue = field.getValue();
        if (fieldValue.isObject()) {
            final JsonObject jsonObject = fieldValue.asObject();
            if (!jsonObject.isEmpty()) {
                jsonObject.forEach(jsonField -> collectFlatPointers(createdPointer.addLeaf(jsonField.getKey()),
                        jsonField, flattenedFields));
            } else {
                flattenedFields.add(new PointerAndValue(createdPointer, fieldValue));
            }
        } else {
            flattenedFields.add(new PointerAndValue(createdPointer, fieldValue));
        }

        return flattenedFields;
    }

    private static JsonObject filterEntries(
            final Collection<PointerAndValue> candidates,
            final Collection<JsonPointer> grantedResources,
            final Collection<JsonPointer> revokedResources,
            final JsonPointer resourcePath) {

        final boolean emptyResourcePath = resourcePath.isEmpty();
        final int levelCount = resourcePath.getLevelCount();
        final PathTrie accessTrie = PathTrie.build(grantedResources, revokedResources);
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        candidates.stream()
                .filter(pointerAndValue -> pointerStartsWith(pointerAndValue.pointer, resourcePath))
                .filter(pointerAndValue -> isAccessible(pointerAndValue.pointer, accessTrie))
                .forEach(pointerAndValue -> {
                    if (emptyResourcePath) {
                        builder.set(pointerAndValue.pointer, pointerAndValue.value);
                    } else {
                        final JsonPointer subPointer = pointerAndValue.pointer.getSubPointer(levelCount).orElseThrow(() -> {
                            final String msgPattern = "JsonPointer did not contain a sub-pointer for level <{0}>!";
                            return new IllegalStateException(MessageFormat.format(msgPattern, levelCount));
                        });
                        builder.set(resourcePath.append(subPointer), pointerAndValue.value);
                    }
                });

        return builder.build()
                .getValue(resourcePath)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElseGet(JsonFactory::newObject);
    }

    private static JsonPointer getPrefixPointerOrThrow(final JsonPointer pointer, final int level) {
        return pointer.getPrefixPointer(level).orElseThrow(() -> {
            final String msgPatten = "JsonPointer did not contain a prefix pointer for level <{0}>!";
            return new NullPointerException(MessageFormat.format(msgPatten, level));
        });
    }

    /**
     * Checks if a pointer starts with the given prefix pointer (proper pointer-level comparison, not string-based).
     *
     * @param pointer the pointer to check
     * @param prefix the prefix pointer
     * @return true if pointer starts with prefix
     */
    private static boolean pointerStartsWith(final JsonPointer pointer, final JsonPointer prefix) {
        if (prefix.getLevelCount() == 0) {
            return true;
        }
        if (pointer.getLevelCount() < prefix.getLevelCount()) {
            return false;
        }
        return pointer.getPrefixPointer(prefix.getLevelCount())
                .map(prefixPointer -> prefixPointer.equals(prefix))
                .orElse(false);
    }

    /**
     * Tests whether {@code pointer} is accessible given the {@code accessTrie} of granted+revoked
     * resource paths for the subject. Walks the trie alongside the pointer's segments — O(depth)
     * with no per-call allocation, replacing the previous variant that allocated a new prefix
     * pointer + did a {@code HashSet.contains} (with a full pointer hash) at every depth level.
     *
     * @param pointer the pointer to check.
     * @param accessTrie the precomputed trie of granted+revoked paths for a single subject.
     * @return {@code true} iff the pointer is accessible to that subject.
     */
    private static boolean isAccessible(final JsonPointer pointer, final PathTrie accessTrie) {
        boolean accessible = accessTrie.granted && !accessTrie.revoked;
        PathTrie node = accessTrie;
        for (final JsonKey key : pointer) {
            node = node.children.get(key.toString());
            if (node == null) {
                break;
            }
            if (node.granted) {
                accessible = true;
            }
            if (node.revoked) {
                accessible = false;
            }
        }
        return accessible;
    }

    /**
     * Compact prefix tree of granted/revoked resource paths for a single subject. Built once per
     * {@code extractAccessiblePaths}/{@code filterEntries} invocation and consulted O(depth)-per-
     * candidate by {@link #isAccessible(JsonPointer, PathTrie)}.
     */
    private static final class PathTrie {

        final Map<String, PathTrie> children = new HashMap<>();
        boolean granted;
        boolean revoked;

        static PathTrie build(final Collection<JsonPointer> grantedPaths,
                final Collection<JsonPointer> revokedPaths) {

            final PathTrie root = new PathTrie();
            for (final JsonPointer path : grantedPaths) {
                markPath(root, path).granted = true;
            }
            for (final JsonPointer path : revokedPaths) {
                markPath(root, path).revoked = true;
            }
            return root;
        }

        private static PathTrie markPath(final PathTrie root, final JsonPointer path) {
            PathTrie node = root;
            for (final JsonKey key : path) {
                final String keyStr = key.toString();
                PathTrie child = node.children.get(keyStr);
                if (child == null) {
                    child = new PathTrie();
                    node.children.put(keyStr, child);
                }
                node = child;
            }
            return node;
        }
    }

    private static Set<JsonPointer> extractAccessiblePaths(
            final Collection<PointerAndValue> candidates,
            final Collection<JsonPointer> grantedResources,
            final Collection<JsonPointer> revokedResources,
            final JsonPointer resourcePath) {

        final boolean emptyResourcePath = resourcePath.isEmpty();
        final int levelCount = resourcePath.getLevelCount();
        final PathTrie accessTrie = PathTrie.build(grantedResources, revokedResources);
        final Set<JsonPointer> accessiblePaths = new HashSet<>();

        final Set<JsonPointer> candidatePaths = new HashSet<>();
        candidates.stream()
                .filter(pointerAndValue -> pointerStartsWith(pointerAndValue.pointer, resourcePath))
                .filter(pointerAndValue -> isAccessible(pointerAndValue.pointer, accessTrie))
                .forEach(pointerAndValue -> {
                    if (emptyResourcePath) {
                        // resourcePath empty → getSubPointer(0) is the pointer itself, append is identity.
                        candidatePaths.add(pointerAndValue.pointer);
                    } else {
                        final JsonPointer subPointer = pointerAndValue.pointer.getSubPointer(levelCount).orElseThrow(() -> {
                            final String msgPattern = "JsonPointer did not contain a sub-pointer for level <{0}>!";
                            return new IllegalStateException(MessageFormat.format(msgPattern, levelCount));
                        });
                        candidatePaths.add(resourcePath.append(subPointer));
                    }
                });
        
        final int resourcePathLevels = resourcePath.getLevelCount();
        
        for (final JsonPointer candidatePath : candidatePaths) {
            boolean hasRevokedChild = false;
            
            final Optional<JsonPointer> candidateRelativeOpt = candidatePath.getSubPointer(resourcePathLevels);
            if (!candidateRelativeOpt.isPresent()) {
                continue;
            }
            final JsonPointer candidateRelative = candidateRelativeOpt.get();
            final String candidatePathStr = candidateRelative.toString();
            
            for (final JsonPointer revokedPath : revokedResources) {
                final Optional<JsonPointer> revokedRelativeOpt = revokedPath.getSubPointer(resourcePathLevels);
                if (!revokedRelativeOpt.isPresent()) {
                    continue;
                }
                final JsonPointer revokedRelative = revokedRelativeOpt.get();
                final String revokedPathStr = revokedRelative.toString();
                
                if (revokedPathStr.equals(candidatePathStr) || revokedPathStr.startsWith(candidatePathStr + "/")) {
                    hasRevokedChild = true;
                    break;
                }
            }
            
            if (!hasRevokedChild) {
                accessiblePaths.add(candidatePath);
            }
        }

        return accessiblePaths;
    }

    private EffectedResources getGrantedAndRevokedSubResource(final JsonPointer resource,
            final String type,
            final Iterable<String> subjectIds,
            final Permissions permissions) {

        final Set<PointerAndPermission> revokedResources = new HashSet<>();
        final Set<PointerAndPermission> grantedResources = permissions.stream()
                .map(permission -> {
                    final EffectedResources result =
                            checkPermissionOnAnySubResource(resource, type, subjectIds, permission);
                    revokedResources.addAll(result.getRevokedResources());
                    return result.getGrantedResources();
                })
                .reduce(TreeBasedPolicyEnforcer::retainElements)
                .orElseGet(Collections::emptySet);

        final Set<PointerAndPermission> clearedGrantedResources =
                removeDeeperRevokes(resource, grantedResources, revokedResources);

        return EffectedResources.of(clearedGrantedResources, revokedResources);
    }

    private static Set<PointerAndPermission> removeDeeperRevokes(final JsonPointer resource,
            final Iterable<PointerAndPermission> grantedResources,
            final Collection<PointerAndPermission> revokedResources) {

        final Set<PointerAndPermission> cleared = new HashSet<>();
        grantedResources.forEach(pp -> {
                    final JsonPointer pointer = pp.pointer;

                    if (revokedResources.stream().noneMatch(rp -> resource.getLevelCount() > pointer.getLevelCount()
                            && rp.permission.equals(pp.permission)
                            && rp.pointer.getLevelCount() >= pointer.getLevelCount()
                            && Objects.equals(getPrefixPointerOrThrow(rp.pointer, pointer.getLevelCount()), pointer)
                    )) {
                        cleared.add(pp);
                    }
                }
        );

        return cleared;
    }

    private static Set<PointerAndPermission> retainElements(final Collection<PointerAndPermission> grans1,
            final Collection<PointerAndPermission> grans2) {

        final Set<JsonPointer> grans2Pointers = grans2.stream().map(pp -> pp.pointer).collect(Collectors.toSet());
        return grans1.stream().filter(pp -> grans2Pointers.contains(pp.pointer)).collect(Collectors.toSet());
    }

    /**
     * Checks the read permissions on a given resourcePath
     * and returns a wrapper which holds all resourcePath the user is allowed to see and all revoked resources.
     *
     * @param resourcePath the path of the Resource to check the permission on.
     * @param resourceType the type of the Resource to check the permission on.
     * @param subjectIds the subjectIds to check for.
     * @param permission the permission to check for.
     * @return the EffectedResources.
     */
    private EffectedResources checkPermissionOnAnySubResource(final JsonPointer resourcePath,
            final String resourceType,
            final Iterable<String> subjectIds,
            final String permission) {

        final Set<PointerAndPermission> grantedResources = new HashSet<>();
        final Set<PointerAndPermission> revokedResources = new HashSet<>();
        subjectIds.forEach(s -> traverseSubtreeForPermissionAccess(permission, resourcePath, resourceType, tree.get(s),
                grantedResources, revokedResources, 0, true));
        return EffectedResources.of(grantedResources, revokedResources);
    }

    private static void traverseSubtreeForPermissionAccess(final String permission,
            final JsonPointer resource,
            final String type,
            @Nullable final PolicyTreeNode policyTreeNode,
            final Set<PointerAndPermission> grantedResources,
            final Set<PointerAndPermission> revokedResources,
            final int level,
            final boolean followingResource) {

        if (policyTreeNode == null) {
            return;
        }
        if (policyTreeNode instanceof SubjectNode) {
            final Optional<PolicyTreeNode> nodeChildOptional = policyTreeNode.getChild(type);
            if (ROOT_RESOURCE.equals(resource.toString())) {
                nodeChildOptional.ifPresent(
                        policyTreeNode1 -> traverseSubtreeForPermissionAccess(permission, resource, type,
                                policyTreeNode1, grantedResources, revokedResources, level, false));
            } else if (nodeChildOptional.isPresent()) {
                traverseSubtreeForPermissionAccess(permission, resource, type, nodeChildOptional.get(),
                        grantedResources, revokedResources, level, true);
            } else {
                resource.get(level)
                        .flatMap(jsonKey -> policyTreeNode.getChild(jsonKey.toString()))
                        .ifPresent(child -> traverseSubtreeForPermissionAccess(permission, resource, type, child,
                                grantedResources, revokedResources, level + 1, true));
            }
        } else {
            final ResourceNode resourceNode = (ResourceNode) policyTreeNode;

            addPermission(permission, resource, grantedResources, revokedResources, level, resourceNode);

            final Optional<JsonKey> jsonKeyOptional = resource.get(level);
            if (followingResource && jsonKeyOptional.isPresent()) {
                policyTreeNode.getChild(jsonKeyOptional.get().toString())
                        .ifPresent(child -> traverseSubtreeForPermissionAccess(permission, resource, type, child,
                                grantedResources, revokedResources, level + 1, true));
            } else {
                policyTreeNode.getChildren()
                        .forEach((s, child) -> traverseSubtreeForPermissionAccess(permission,
                                resource.addLeaf(JsonKey.of(s)), type, child, grantedResources,
                                revokedResources, level + 1, false));
            }
        }
    }

    private static void addPermission(final String permission,
            final JsonPointer resource,
            final Collection<PointerAndPermission> grantedResources,
            final Collection<PointerAndPermission> revokedResources,
            final int level,
            final ResourceNode resourceNode) {

        final JsonPointer resourceToAdd = ROOT_RESOURCE.equals(resource.toString())
                ? ROOT_RESOURCE_POINTER
                : getPrefixPointerOrThrow(resource, level);
        final EffectedPermissions effectedPermissions = resourceNode.getPermissions();
        if (effectedPermissions.getGrantedPermissions().contains(permission)) {
            grantedResources.add(new PointerAndPermission(resourceToAdd, permission));
        }
        if (effectedPermissions.getRevokedPermissions().contains(permission)) {
            revokedResources.add(new PointerAndPermission(resourceToAdd, permission));
        }
    }

    /**
     * Wrapper to holds a JsonPointer and a JsonValue.
     */
    @Immutable
    private static final class PointerAndValue {

        private final JsonPointer pointer;
        private final JsonValue value;

        PointerAndValue(final JsonPointer pointer, final JsonValue value) {
            this.pointer = pointer;
            this.value = value;
        }
    }

    /**
     * Wrapper for JsonPointer with its according permission.
     */
    @Immutable
    static final class PointerAndPermission {

        private final JsonPointer pointer;
        private final String permission;

        PointerAndPermission(final JsonPointer pointer, final String permission) {
            this.pointer = pointer;
            this.permission = permission;
        }
    }

    /**
     * Memo key for the two permission-check methods. {@code authorizationSubjectIds} is a {@link Set} so its
     * equality is order-independent; {@code partial} distinguishes {@code hasUnrestrictedPermissions}
     * ({@code false}) from {@code hasPartialPermissions} ({@code true}), letting both share a single map. All
     * components are immutable value types with stable {@code equals}/{@code hashCode}. Plain class (not a
     * record) because this module still targets Java 8.
     */
    @Immutable
    private static final class PermissionCheckKey {

        private final ResourceKey resourceKey;
        private final Set<String> authorizationSubjectIds;
        private final Permissions permissions;
        private final boolean partial;

        private PermissionCheckKey(final ResourceKey resourceKey, final Set<String> authorizationSubjectIds,
                final Permissions permissions, final boolean partial) {
            this.resourceKey = resourceKey;
            this.authorizationSubjectIds = authorizationSubjectIds;
            this.permissions = permissions;
            this.partial = partial;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final PermissionCheckKey that = (PermissionCheckKey) o;
            return partial == that.partial
                    && Objects.equals(resourceKey, that.resourceKey)
                    && Objects.equals(authorizationSubjectIds, that.authorizationSubjectIds)
                    && Objects.equals(permissions, that.permissions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourceKey, authorizationSubjectIds, permissions, partial);
        }
    }

    /**
     * Memo key for the subject-collection methods, which classify <em>all</em> subjects and therefore depend
     * only on the resource and permissions. Plain class (not a record) because this module still targets Java 8.
     */
    @Immutable
    private static final class SubjectsKey {

        private final ResourceKey resourceKey;
        private final Permissions permissions;

        private SubjectsKey(final ResourceKey resourceKey, final Permissions permissions) {
            this.resourceKey = resourceKey;
            this.permissions = permissions;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SubjectsKey that = (SubjectsKey) o;
            return Objects.equals(resourceKey, that.resourceKey) && Objects.equals(permissions, that.permissions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourceKey, permissions);
        }
    }

}
