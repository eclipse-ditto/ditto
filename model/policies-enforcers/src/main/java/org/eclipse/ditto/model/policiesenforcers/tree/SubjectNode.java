/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policiesenforcers.tree;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link PolicyTreeNode} for subject nodes.
 */
final class SubjectNode implements PolicyTreeNode {

    private final String name;
    private final Map<String, PolicyTreeNode> children = new HashMap<>();

    private SubjectNode(final String name) {
        this.name = name;
    }

    /**
     * Returns a new {@code SubjectNode} for the given {@code name}.
     *
     * @param name the name.
     * @return the SubjectNode.
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static SubjectNode of(final String name) {
        checkNotNull(name, "Name");

        return new SubjectNode(name);
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
