/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.rql.predicates.ast;


/**
 * Base interface for all predicate nodes in an AST. Provides the basic method for the visitor pattern, too.
 */
public interface Node {

    /**
     * PredicateVisitor Pattern. Takes a visitor as parameter and calls the corresponding visit method with itself as
     * parameter.
     *
     * @param predicateVisitor the visitor which should be called.
     */
    void accept(PredicateVisitor predicateVisitor);
}
