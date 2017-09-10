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
package org.eclipse.ditto.model.thingsearchparser.options.rql;

import java.util.List;

import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearchparser.RqlParserBase;
import org.eclipse.ditto.model.thingsearchparser.predicates.rql.RqlPredicateParser;
import org.parboiled.parserunners.ReportingParseRunner;

/**
 * RQL Parser. Parses options in the RQL "standard" according to https://github.com/persvr/rql with the following EBNF:
 * <pre>
 * Options                    = Option, { ',', Option }
 * Option                     = Sort | Limit
 * Sort                       = "sort", '(', SortProperty, { ',', SortProperty }, ')'
 * SortProperty               = SortOrder, PropertyLiteral
 * SortOrder                  = '+' | '-'
 * Limit                      = "limit", '(', IntegerLiteral, ',', IntegerLiteral, ')'
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
public final class RqlOptionParser extends RqlParserBase<OptionRules, List<Option>> implements OptionParser {

    /**
     * The default {@link org.parboiled.parserunners.ParseRunner}.
     */
    private static final ParseRunnerProvider DEFAULT_PARSE_RUNNER_PROVIDER = ReportingParseRunner::new;

    /**
     * Creates a new RQL parser with the given {@link RqlPredicateParser.ParseRunnerProvider}.
     *
     * @param parseRunnerProvider instance of a {@code RqlPredicateParser.ParseRunnerProvider}.
     * @throws NullPointerException if {@code parseRunnerProvider} is {@code null}.
     */
    public RqlOptionParser(final ParseRunnerProvider parseRunnerProvider) {
        super(parseRunnerProvider, OptionRules.class);
    }

    /**
     * Creates a new RQL parser using the default ParseRunnerProvider as {@link RqlOptionParser.ParseRunnerProvider}.
     */
    public RqlOptionParser() {
        this(DEFAULT_PARSE_RUNNER_PROVIDER);
    }

}
