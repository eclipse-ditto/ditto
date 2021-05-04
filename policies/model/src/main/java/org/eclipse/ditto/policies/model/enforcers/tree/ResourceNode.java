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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Permissions;

/**
 * Implementation of {@link org.eclipse.ditto.policies.model.enforcers.tree.PolicyTreeNode} for resource nodes.
 */
@NotThreadSafe
final class ResourceNode implements PolicyTreeNode {

    private final PolicyTreeNode parent;
    private final String name;
    private final Map<String, PolicyTreeNode> children;
    private EffectedPermissions permissions;

    // this field is for internal caching only: do not use in hashCode() and equals() or toString()
    private JsonPointer absolutePointer;

    private ResourceNode(final PolicyTreeNode parent, final String name, final EffectedPermissions permissions) {
        this.parent = parent;
        this.name = name;
        children = new HashMap<>();
        this.permissions = permissions;
        absolutePointer = null;
    }

    /**
     * Returns a new {@code ResourceNode} for the given {@code parent} and {@code name}.
     *
     * @param parent the parent node of this resource node.
     * @param name the sub resource name of this node.
     * @return the ResourceNode.
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ResourceNode of(final PolicyTreeNode parent, final String name) {
        return of(parent, name, EffectedPermissions.newInstance(Permissions.none(), Permissions.none()));
    }

    /**
     * Returns a new {@code ResourceNode} for the given {@code parent}, {@code name}, {@code permissions}
     * and {@code children}.
     *
     * @param parent the parent node of this resource node.
     * @param name the sub resource name of this node.
     * @param permissions the permissions on this level.
     * @return the ResourceNode.
     * @throws NullPointerException if any argument is {@code null}
     */
    public static ResourceNode of(final PolicyTreeNode parent, final String name,
            final EffectedPermissions permissions) {
        checkNotNull(parent, "Parent");
        checkNotNull(name, "Name");
        checkNotNull(permissions, "Permissions");

        return new ResourceNode(parent, name, permissions);
    }

    @Override
    public String getName() {
        return name;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.RESOURCE;
    }

    @Override
    public Optional<PolicyTreeNode> getChild(final String resourceName) {
        return Optional.ofNullable(children.get(resourceName));
    }

    @Override
    public Map<String, PolicyTreeNode> getChildren() {
        return children;
    }

    @Override
    public void addChild(final PolicyTreeNode childNode) {
        children.put(childNode.getName(), childNode);
    }

    @Override
    public void accept(@Nonnull final Visitor visitor) {
        visitor.visitTreeNode(this);
        for (final PolicyTreeNode child : children.values()) {
            child.accept(visitor);
        }
    }

    public Optional<PolicyTreeNode> getParent() {
        return Optional.of(parent);
    }

    public EffectedPermissions getPermissions() {
        return permissions;
    }

    public void setPermissions(final EffectedPermissions permissions) {
        this.permissions = permissions;
    }

    /**
     * Indicates whether the effected permissions of this node grant the specified permission and do not revoke it.
     *
     * @param possiblyGrantedPermission a permission to be evaluated against the effected permissions of this node.
     * @param furtherPossiblyGrantedPermissions further permissions to be evaluated against the effected permissions
     * of this node.
     * @return {@code true} if the effected granted permissions of this node contain {@code possiblyGrantedPermission}
     * and {@code furtherPossiblyGrantedPermissions} and non is revoked, {@code false} else.
     * @throws NullPointerException if any argument is {@code null}.
     * @see #areAllGranted(java.util.Collection)
     */
    public boolean isGranted(@Nonnull final String possiblyGrantedPermission,
            @Nonnull final String ... furtherPossiblyGrantedPermissions) {
        checkNotNull(possiblyGrantedPermission, "permission to be evaluated");
        checkNotNull(furtherPossiblyGrantedPermissions, "further permissions to be evaluated");

        final Collection<String> p = new HashSet<>(1 + furtherPossiblyGrantedPermissions.length);
        p.add(possiblyGrantedPermission);
        Collections.addAll(p, furtherPossiblyGrantedPermissions);

        return areAllGranted(p);
    }

    /**
     * Indicates whether the effected permissions of this node grant all of the specified permissions and do revoke
     * none of them.
     *
     * @param possiblyGrantedPermissions the permissions to be evaluated against the effected permissions of this node.
     * @return {@code true} if the effected granted permissions of this node contain all of
     * {@code possiblyGrantedPermissions} and none of {@code possiblyGrantedPermissions} is revoked, {@code false} else.
     * @throws NullPointerException if {@code possiblyGrantedPermissions} is {@code null}.
     */
    public boolean areAllGranted(@Nonnull final Collection<String> possiblyGrantedPermissions) {
        checkPermissionsToBeEvaluated(possiblyGrantedPermissions);

        final Permissions actuallyGrantedPermissions = permissions.getGrantedPermissions();
        final Permissions actuallyRevokedPermissions = permissions.getRevokedPermissions();

        final boolean areAllGranted = actuallyGrantedPermissions.containsAll(possiblyGrantedPermissions);
        final boolean isNoneRevoked = Collections.disjoint(actuallyRevokedPermissions, possiblyGrantedPermissions);

        return areAllGranted && isNoneRevoked;
    }

    private static void checkPermissionsToBeEvaluated(final Collection<String> permissions) {
        checkNotNull(permissions, "permissions to be evaluated");
    }

    /**
     * Indicates whether the effected permissions of this node revoke all of the specified permissions.
     *
     * @param possiblyRevokedPermissions the permissions the be evaluated against the effected revoked permissions of
     * this node.
     * @return {@code true} if the effected revoked permissions of this node contain all of
     * {@code possiblyRevokedPermissions}, {@code false} else.
     * @throws NullPointerException if {@code possiblyRevokedPermissions} is {@code null}.
     */
    public boolean areAllRevoked(@Nonnull final Collection<String> possiblyRevokedPermissions) {
        checkPermissionsToBeEvaluated(possiblyRevokedPermissions);

        final Permissions actuallyRevokedPermissions = permissions.getRevokedPermissions();

        return actuallyRevokedPermissions.containsAll(possiblyRevokedPermissions);
    }

    /**
     * Returns the absolute pointer of this resource node.
     *
     * @return the pointer.
     */
    @Nonnull
    public JsonPointer getAbsolutePointer() {
        // not thread safe lazy initialization of the absolute pointer
        JsonPointer result = absolutePointer;
        if (null != result) {
            return result;
        }
        result = getAbsolutePointer(this);
        absolutePointer = result;
        return result;
    }

    private JsonPointer getAbsolutePointer(final ResourceNode resourceNode) {
        final JsonPointer result = resourceNode.getParent()
                .filter(p -> Type.RESOURCE == p.getType())
                .map(ResourceNode.class::cast)
                .map(this::getAbsolutePointer) // Recursion!
                .orElseGet(JsonFactory::emptyPointer);

        return result.addLeaf(JsonFactory.newKey(resourceNode.getName()));
    }

    /**
     * If this node is part of a tree, e. g. it has a parent and/or children, this method returns the level where
     * this node is located within this tree.
     *
     * @return the level of this node within its tree.
     */
    public int getLevel() {
        final JsonPointer absoluteJsonPointer = getAbsolutePointer();
        return absoluteJsonPointer.getLevelCount();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ResourceNode that = (ResourceNode) o;
        return Objects.equals(parent, that.parent) &&
                Objects.equals(name, that.name) &&
                Objects.equals(permissions, that.permissions) &&
                Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, name, permissions, children);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "parent=" + parent.getName() +
                ", name=" + name +
                ", permissions=" + permissions +
                ", children=" + children +
                ']';
    }

}
