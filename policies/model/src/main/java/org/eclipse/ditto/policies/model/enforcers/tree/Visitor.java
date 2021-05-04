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

import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * This interface defines a visitor for {@link org.eclipse.ditto.policies.model.enforcers.tree.PolicyTreeNode}s according to the <em>Visitor Design Pattern.</em>
 * <p>
 * Each Visitor is also a Supplier to provide access to the gathered data.
 *
 * @param <T> the type of the result this visitor gathers.
 */
@ParametersAreNonnullByDefault
interface Visitor<T> extends Supplier<T> {

    /**
     * Adds the specified tree node as input for this visitor's custom operation logic.
     *
     * @param node the node to be visited.
     */
    void visitTreeNode(PolicyTreeNode node);

}
