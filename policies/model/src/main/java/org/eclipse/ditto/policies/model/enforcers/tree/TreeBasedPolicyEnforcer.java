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

/**
 * Holds Algorithms to create a policy tree and to perform different policy checks on this tree.
 */
public final class TreeBasedPolicyEnforcer implements Enforcer {

    private static final String ROOT_RESOURCE = "/";

    /**
     * Maps subject ID to {@link org.eclipse.ditto.policies.model.enforcers.tree.SubjectNode} whose children are {@link org.eclipse.ditto.policies.model.enforcers.tree.ResourceNode} for which the subject is granted
     * or revoked access. The child-set of each {@link org.eclipse.ditto.policies.model.enforcers.tree.SubjectNode} is effectively a map from resources to permissions.
     */
    private final Map<String, PolicyTreeNode> tree;

    private TreeBasedPolicyEnforcer(final Map<String, PolicyTreeNode> tree) {
        this.tree = tree;
    }

    /**
     * Creates a new policy tree for execution of policy checks.
     *
     * @param policyEntries the policy entries to create a tree for
     * @return the generated {@code TreeBasedPolicyEnforcer}
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static TreeBasedPolicyEnforcer createInstance(final Iterable<PolicyEntry> policyEntries) {
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

        return new TreeBasedPolicyEnforcer(tree);
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
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        final Collection<String> authSubjectIds = getAuthorizationSubjectIds(authorizationContext);
        return visitTree(new CheckUnrestrictedPermissionsVisitor(resourcePointer, authSubjectIds, permissions));
    }

    private static void checkPermissions(final Permissions permissions) {
        checkNotNull(permissions, "permissions to check");
    }

    private static JsonPointer createAbsoluteResourcePointer(final ResourceKey resourceKey) {
        return JsonFactory.newPointer(resourceKey.getResourceType()).append(resourceKey.getResourcePath());
    }

    private static Collection<String> getAuthorizationSubjectIds(final AuthorizationContext authorizationContext) {
        checkNotNull(authorizationContext, "Authorization Context");

        return authorizationContext.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet());
    }

    private <T> T visitTree(final Visitor<T> visitor) {
        tree.values().forEach(policyTreeNode -> policyTreeNode.accept(visitor));
        return visitor.get();
    }

    @Override
    public EffectedSubjects getSubjectsWithPermission(final ResourceKey resourceKey, final Permissions permissions) {
        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        return visitTree(new CollectEffectedSubjectsVisitor(resourcePointer, permissions));
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
        return visitTree(new CollectPartialGrantedSubjectsVisitor(resourcePointer, permissions));
    }

    @Override
    public boolean hasPartialPermissions(final ResourceKey resourceKey, final AuthorizationContext authorizationContext,
            final Permissions permissions) {

        checkResourceKey(resourceKey);
        checkPermissions(permissions);
        final Collection<String> authSubjectIds = getAuthorizationSubjectIds(authorizationContext);
        final JsonPointer resourcePointer = createAbsoluteResourcePointer(resourceKey);
        return visitTree(new CheckPartialPermissionsVisitor(resourcePointer, authSubjectIds, permissions));
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
                JsonFactory.newPointer(ROOT_RESOURCE), resourceKey.getResourceType(), authorizationSubjectIds,
                permissions);

        if (jsonFields instanceof JsonObject && ((JsonObject) jsonFields).isNull()) {
            return JsonFactory.nullObject();
        }

        final List<PointerAndValue> flatPointers = new ArrayList<>();
        jsonFields.forEach(jsonField -> collectFlatPointers(jsonField.getKey().asPointer(), jsonField, flatPointers));
        final Set<JsonPointer> grantedResources = extractJsonPointers(effectedResources.getGrantedResources());
        final Set<JsonPointer> revokedResources = extractJsonPointers(effectedResources.getRevokedResources());

        final JsonPointer resourcePath = resourceKey.getResourcePath();
        final List<PointerAndValue> prefixedPointers = flatPointers.stream()
                .map(pv -> new PointerAndValue(resourcePath.append(pv.pointer), pv.value))
                .collect(Collectors.toList());
        return filterEntries(prefixedPointers, grantedResources, revokedResources, resourcePath);
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

        final int levelCount = resourcePath.getLevelCount();
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        candidates.stream()
                .filter(pointerAndValue -> pointerAndValue.pointer.toString().startsWith(resourcePath.toString()))
                .filter(pointerAndValue -> {
                    final JsonPointer rootResourcePointer = JsonFactory.newPointer(ROOT_RESOURCE);
                    boolean accessible = grantedResources.contains(rootResourcePointer) &&
                            !revokedResources.contains(rootResourcePointer);

                    final JsonPointer pointer = pointerAndValue.pointer;

                    for (int i = 1; i <= pointer.getLevelCount(); i++) {
                        if (containsPrefixPointer(getPrefixPointerOrThrow(pointer, i), grantedResources)) {
                            accessible = true;
                        }
                        // no else if -> revoked counts more on the same level.
                        if (containsPrefixPointer(getPrefixPointerOrThrow(pointer, i), revokedResources)) {
                            accessible = false;
                        }
                    }
                    return accessible;
                })
                .forEach(pointerAndValue -> {
                    final JsonPointer subPointer = pointerAndValue.pointer.getSubPointer(levelCount).orElseThrow(() -> {
                        final String msgPattern = "JsonPointer did not contain a sub-pointer for level <{0}>!";
                        return new NullPointerException(MessageFormat.format(msgPattern, levelCount));
                    });
                    builder.set(resourcePath.append(subPointer), pointerAndValue.value);
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

    private static boolean containsPrefixPointer(final JsonPointer prefix, final Collection<JsonPointer> resources) {
        return resources.stream().anyMatch(p -> p.equals(prefix));
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
                resource.get(level).ifPresent(jsonKey -> policyTreeNode.getChild(jsonKey.toString())
                        .ifPresent(child -> traverseSubtreeForPermissionAccess(permission, resource, type, child,
                                grantedResources, revokedResources, level + 1, true)));
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
                ? JsonFactory.newPointer(ROOT_RESOURCE)
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

}
