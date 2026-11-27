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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Permissions;

/**
 * Immutable implementation of {@link org.eclipse.ditto.policies.model.enforcers.tree.PolicyTreeNode} for resource
 * nodes.
 * <p>
 * A node knows its absolute position instead of its parent, so equal subtrees are shared: between the subjects of a
 * policy and between policies (see {@link SharedTreeNodes}).
 * </p>
 */
@Immutable
final class ResourceNode implements PolicyTreeNode {

    private final String name;
    private final JsonPointer absolutePointer;
    private final EffectedPermissions permissions;
    private final Map<String, PolicyTreeNode> children;
    private final int hashCode;

    private ResourceNode(final String name, final JsonPointer absolutePointer, final EffectedPermissions permissions,
            final Map<String, PolicyTreeNode> children) {

        this.name = name;
        this.absolutePointer = absolutePointer;
        this.permissions = permissions;
        this.children = children;
        hashCode = hash(name, absolutePointer, permissions, children);
    }

    private static int hash(final String name, final JsonPointer absolutePointer,
            final EffectedPermissions permissions, final Map<String, PolicyTreeNode> children) {
        return Objects.hash(name, absolutePointer, permissions, children);
    }

    /**
     * Returns a resource node with the given properties, sharing an existing equal node.
     *
     * @param name the sub resource name of this node.
     * @param absolutePointer the absolute pointer of this node, starting with the resource type.
     * @param permissions the permissions on this level.
     * @param children the (already shared) child nodes by their names; the map must not be modified afterwards.
     * @return the ResourceNode.
     * @throws NullPointerException if any argument is {@code null}
     */
    static ResourceNode of(final String name, final JsonPointer absolutePointer,
            final EffectedPermissions permissions, final Map<String, PolicyTreeNode> children) {

        checkNotNull(name, "Name");
        checkNotNull(absolutePointer, "absolute pointer");
        checkNotNull(permissions, "Permissions");
        checkNotNull(children, "children");

        final int hash = hash(name, absolutePointer, permissions, children);
        final ResourceNode cached = SharedTreeNodes.get(hash);
        if (null != cached && cached.hasProperties(hash, name, absolutePointer, permissions, children)) {
            return cached;
        }
        return SharedTreeNodes.put(new ResourceNode(name, absolutePointer, permissions,
                children.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(children)));
    }

    private boolean hasProperties(final int hash, final String name, final JsonPointer absolutePointer,
            final EffectedPermissions permissions, final Map<String, PolicyTreeNode> children) {
        return hashCode == hash &&
                this.name.equals(name) &&
                this.absolutePointer.equals(absolutePointer) &&
                this.permissions.equals(permissions) &&
                this.children.equals(children);
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
    public void accept(@Nonnull final Visitor visitor) {
        visitor.visitTreeNode(this);
        for (final PolicyTreeNode child : children.values()) {
            child.accept(visitor);
        }
    }

    public EffectedPermissions getPermissions() {
        return permissions;
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

        final Collection<String> p = new HashSet<>(1 + furtherPossiblyGrantedPermissions.length, 1.0f);
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
     * Returns the absolute pointer of this resource node, starting with the resource type.
     *
     * @return the pointer.
     */
    @Nonnull
    public JsonPointer getAbsolutePointer() {
        return absolutePointer;
    }

    public int getLevel() {
        return absolutePointer.getLevelCount();
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
        return hashCode == that.hashCode &&
                Objects.equals(name, that.name) &&
                Objects.equals(absolutePointer, that.absolutePointer) &&
                Objects.equals(permissions, that.permissions) &&
                Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "absolutePointer=" + absolutePointer +
                ", permissions=" + permissions +
                ", children=" + children +
                ']';
    }

}
