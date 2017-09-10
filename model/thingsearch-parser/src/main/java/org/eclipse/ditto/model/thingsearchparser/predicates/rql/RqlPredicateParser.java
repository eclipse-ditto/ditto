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

import org.eclipse.ditto.model.thingsearchparser.RqlParserBase;
import org.eclipse.ditto.model.thingsearchparser.predicates.PredicateParser;
import org.eclipse.ditto.model.thingsearchparser.predicates.ast.RootNode;
import org.parboiled.parserunners.ReportingParseRunner;


/**
 * RQL Parser. Parses predicates in the RQL "standard" according to https://github.com/persvr/rql with the following
 * EBNF:
 * <pre>
 * Query                      = SingleComparisonOp | MultiComparisonOp | MultiLogicalOp | SingleLogicalOp
 * MultiLogicalOp             = MultiLogicalName, '(', Query, { ',', Query }, ')'
 * MultiLogicalName           = "and" | "or"
 * SingleLogicalOp            = SingleLogicalName, '(', Query, ')'
 * SingleLogicalName          = "not"
 * ComparisonProperty         = PropertyLiteral
 * ComparisonValue            = Literal
 * SingleComparisonOp         = SingleComparisonName, '(', ComparisonProperty, ',', ComparisonValue, ')'
 * SingleComparisonName       = "eq" | "ne" | "gt" | "ge" | "lt" | "le" | "like"
 * MultiComparisonOp          = MultiComparisonName, '(', ComparisonProperty, ',', ComparisonValue, { ',',
 * ComparisonValue }, ')'
 * MultiComparisonName        = "in"
 * Literal                    = FloatLiteral | IntegerLiteral | StringLiteral | "true" | "false" | "null"
 * FloatLiteral               = [ '+' | '-' ], "0.", Digit, { Digit } |
 *                              [ '+' | '-' ], DigitWithoutZero, { Digit }, '.', Digit, { Digit }
 * IntegerLiteral             = '0' | [ '+' | '-' ], DigitWithoutZero, { Digit }
 * DigitWithoutZero           = '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
 * Digit                      = '0' | DigitWithoutZero
 * StringLiteral              = '"', ? printable characters ?, '"'
 * PropertyLiteral            = ? printable characters ?
 * </pre>
 */
public class RqlPredicateParser extends RqlParserBase<PredicateRules, RootNode> implements PredicateParser {

    /**
     * The default {@link org.parboiled.parserunners.ParseRunner}.
     */
    private static final ParseRunnerProvider DefaultParseRunnerProvider = ReportingParseRunner::new;

    /**
     * Creates a new RQL parser with the given {@link RqlPredicateParser.ParseRunnerProvider}.
     *
     * @param parseRunnerProvider instance of a {@link RqlPredicateParser.ParseRunnerProvider}.
     */
    public RqlPredicateParser(final ParseRunnerProvider parseRunnerProvider) {
        super(parseRunnerProvider, PredicateRules.class);
    }

    /**
     * Creates a new RQL parser with {@link RqlPredicateParser#DefaultParseRunnerProvider} as {@link
     * RqlPredicateParser.ParseRunnerProvider}.
     */
    public RqlPredicateParser() {
        this(DefaultParseRunnerProvider);
    }
}
