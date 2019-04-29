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
package org.eclipse.ditto.model.enforcers.tree;

import java.util.Map;
import java.util.Optional;

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
     * Evaluate this node according to a {@link Visitor}.
     *
     * @param visitor the visitor which evaluates this node.
     * @throws NullPointerException if {@code visitor} is {@code null}.
     */
    void accept(@Nonnull Visitor visitor);

}
