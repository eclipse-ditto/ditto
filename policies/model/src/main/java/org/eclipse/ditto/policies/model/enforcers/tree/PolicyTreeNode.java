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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;

/**
 * Interface for all nodes in a policy tree.
 */
interface PolicyTreeNode {

    /**
     * An enumeration of types of policy tree nodes.
     *
     */
    enum Type {
        RESOURCE,
        SUBJECT
    }

    /**
     * Returns the name of this node.
     *
     * @return the resource name
     */
    String getName();

    /**
     * Returns the type of this node.
     *
     * @return the type.
     */
    @Nonnull
    Type getType();

    /**
     * Returns an optional which holds the child for the sub resource {@code resourceName} or an empty optional if this
     * child does not exist.
     *
     * @param resourceName the sub resource name of the child.
     * @return an optional containing the child or empty.
     */
    Optional<PolicyTreeNode> getChild(String resourceName);

    /**
     * Returns the child for the sub resource with the given name.
     * If the child is not yet known the given function is used to added to the children of this node.
     *
     * @param resourceName the sub resource name of the child to be retrieved.
     * @param mappingFunction the mapping function to compute the child.
     * @return the existing or computed child associated with {@code resourceName}.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    default PolicyTreeNode computeIfAbsent(final String resourceName,
            final Function<String, PolicyTreeNode> mappingFunction) {

        checkNotNull(resourceName, "resourceName");
        checkNotNull(mappingFunction, "mappingFunction");

        final Map<String, PolicyTreeNode> children = getChildren();
        return children.computeIfAbsent(resourceName, mappingFunction);
    }

    /**
     * Returns all children of this node.
     *
     * @return all children nodes
     */
    Map<String, PolicyTreeNode> getChildren();

    /**
     * Adds a child node to this node.
     *
     * @param childNode the child node to add
     */
    void addChild(PolicyTreeNode childNode);

    /**
     * Evaluate this node according to a {@link org.eclipse.ditto.policies.model.enforcers.tree.Visitor}.
     *
     * @param visitor the visitor which evaluates this node.
     * @throws NullPointerException if {@code visitor} is {@code null}.
     */
    void accept(@Nonnull Visitor<?> visitor);

}
