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
package org.eclipse.ditto.model.rqlparser;

import org.eclipse.ditto.model.rql.predicates.PredicateParser;
import org.eclipse.ditto.model.rql.predicates.ast.RootNode;
import org.eclipse.ditto.model.rqlparser.internal.RqlPredicateParser$;

/**
 * RQL Parser parsing predicates in the RQL "standard" according to https://github.com/persvr/rql.
 */
public class RqlPredicateParser implements PredicateParser {

    private static final PredicateParser PARSER = RqlPredicateParser$.MODULE$;

    @Override
    public RootNode parse(final String input) {
        return PARSER.parse(input);
    }
}
