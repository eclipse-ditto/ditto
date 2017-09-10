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

import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * This interface defines a visitor for {@link PolicyTreeNode}s according to the <em>Visitor Design Pattern.</em>
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
