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
package org.eclipse.ditto.model.thingsearchparser.predicates;

import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;

/**
 * Interface a predicate parser has to implement.
 */
public interface PredicateParser {

    /**
     * Parse the specified input.
     *
     * @param input the input that should be parsed.
     * @return the AST {@link RootNode} representing the root of the AST.
     * @throws NullPointerException if {@code input} is {@code null}.
     */
    RootNode parse(String input);

}
