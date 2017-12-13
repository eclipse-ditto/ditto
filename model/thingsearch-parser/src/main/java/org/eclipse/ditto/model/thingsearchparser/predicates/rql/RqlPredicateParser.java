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
package org.eclipse.ditto.model.thingsearchparser.predicates.rql;

import org.eclipse.ditto.model.thingsearchparser.parser.RqlPredicateParser$;
import org.eclipse.ditto.model.thingsearchparser.predicates.PredicateParser;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;

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
