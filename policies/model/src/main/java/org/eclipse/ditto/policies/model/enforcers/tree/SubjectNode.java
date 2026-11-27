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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link org.eclipse.ditto.policies.model.enforcers.tree.PolicyTreeNode} for subject
 * nodes. Subjects with equal resources share their (immutable) child nodes.
 */
@Immutable
final class SubjectNode implements PolicyTreeNode {

    private final String name;
    private final Map<String, PolicyTreeNode> children;

    private SubjectNode(final String name, final Map<String, PolicyTreeNode> children) {
        this.name = name;
        this.children = children;
    }

    /**
     * Returns a new {@code SubjectNode} for the given {@code name} and child nodes.
     *
     * @param name the name.
     * @param children the (already shared) resource nodes of the resource types by their names; the map must not be
     * modified afterwards.
     * @return the SubjectNode.
     * @throws NullPointerException if any argument is {@code null}
     */
    public static SubjectNode of(final String name, final Map<String, PolicyTreeNode> children) {
        checkNotNull(name, "Name");
        checkNotNull(children, "children");
        return new SubjectNode(name, children.isEmpty()
                ? Collections.emptyMap()
                : SharedTreeNodes.shareChildren(Collections.unmodifiableMap(children)));
    }

    @Override
    public String getName() {
        return name;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.SUBJECT;
    }

    @Override
    public Optional<PolicyTreeNode> getChild(final String resourceName) {
        return Optional.ofNullable(children.get(resourceName));
    }

    @Override
    public void accept(@Nonnull final Visitor visitor) {
        visitor.visitTreeNode(this);
        for (final PolicyTreeNode child : children.values()) {
            child.accept(visitor);
        }
    }

    @Override
    public Map<String, PolicyTreeNode> getChildren() {
        return children;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SubjectNode that = (SubjectNode) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, children);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "name=" + name +
                ", children=" + children +
                ']';
    }

}
