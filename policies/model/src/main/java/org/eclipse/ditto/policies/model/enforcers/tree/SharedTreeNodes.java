/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Shares equal immutable nodes of policy trees, and equal maps of child nodes, across all subjects of a policy and
 * across all policies: subjects of the same policy entry and policies created from the same templates contain the
 * same resources with the same permissions at the same positions.
 * <p>
 * Lossy, direct-mapped caches: a colliding value replaces the cached one, so memory stays bounded and nodes of
 * unusual policies cannot occupy the caches permanently. Lookups never block; races are benign because only
 * immutable values with final fields are cached.
 * </p>
 */
@ThreadSafe
final class SharedTreeNodes {

    private static final ResourceNode[] RESOURCE_NODES = new ResourceNode[1 << 16];
    private static final Object[] CHILDREN = new Object[1 << 14];

    private SharedTreeNodes() {
        throw new AssertionError();
    }

    /**
     * Returns the cached node in the slot of the given hash code, which might be any node with a colliding hash.
     *
     * @param hashCode the hash code of the wanted node.
     * @return the cached node or {@code null}.
     */
    @Nullable
    static ResourceNode get(final int hashCode) {
        return RESOURCE_NODES[slot(hashCode, RESOURCE_NODES.length)];
    }

    /**
     * Caches the given node, replacing the node in its slot.
     *
     * @param node the immutable node to share.
     * @return {@code node}.
     */
    static ResourceNode put(final ResourceNode node) {
        RESOURCE_NODES[slot(node.hashCode(), RESOURCE_NODES.length)] = node;
        return node;
    }

    /**
     * Returns the cached map equal to {@code candidate} or caches and returns {@code candidate}.
     *
     * @param candidate the unmodifiable map of shared child nodes.
     * @return a map equal to {@code candidate}.
     */
    @SuppressWarnings("unchecked")
    static Map<String, PolicyTreeNode> shareChildren(final Map<String, PolicyTreeNode> candidate) {
        final int slot = slot(candidate.hashCode(), CHILDREN.length);
        final Object cached = CHILDREN[slot];
        if (candidate.equals(cached)) {
            return (Map<String, PolicyTreeNode>) cached;
        }
        CHILDREN[slot] = candidate;
        return candidate;
    }

    private static int slot(final int hash, final int size) {
        return (hash ^ (hash >>> 16)) & (size - 1);
    }

}
