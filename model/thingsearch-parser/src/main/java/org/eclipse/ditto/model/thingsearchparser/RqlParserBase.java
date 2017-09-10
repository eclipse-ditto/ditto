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
package org.eclipse.ditto.model.thingsearchparser;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.errors.ActionError;
import org.parboiled.errors.ParseError;
import org.parboiled.parserunners.ParseRunner;
import org.parboiled.support.ParsingResult;

/**
 * Base class for RQL parser implementations. Does the setup of the parser and the common error handling.
 */
public abstract class RqlParserBase<T extends BaseRules, R> {

    private final T parser;
    private final ParseRunnerProvider parseRunnerProvider;

    /**
     * Creates a new RQL parser with the given {@link RqlParserBase.ParseRunnerProvider}.
     *
     * @param parseRunnerProvider instance of a {@link RqlParserBase.ParseRunnerProvider}.
     * @param parserClass the type of the parser to create.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public RqlParserBase(final ParseRunnerProvider parseRunnerProvider, final Class<T> parserClass) {
        parser = Parboiled.createParser(checkNotNull(parserClass, "parser type"));
        this.parseRunnerProvider = checkNotNull(parseRunnerProvider, "ParseRunnerProvider");
    }

    @SuppressWarnings("unchecked")
    public R parse(final String input) {
        checkNotNull(input, "input");
        final ParsingResult<?> result = parseRunnerProvider.get(parser.root()).run(input);

        if (result.hasErrors()) {
            final StringBuilder sb = new StringBuilder();
            String lastErrorMessage = "";

            for (final ParseError e : result.parseErrors) {
                if (e instanceof ActionError) {
                    final String errorMessage = e.getErrorMessage();
                    if (!errorMessage.equals(lastErrorMessage)) {
                        sb.append(e.getErrorMessage()).append(" ");
                    }
                    lastErrorMessage = errorMessage;
                } else {
                    sb.append(String.format("Error parsing input \"%s\" at index %d. ", input, e.getStartIndex()));
                }
            }

            throw new ParserException(sb.toString());
        }

        return (R) result.resultValue;
    }

    /**
     * Interface you can implement to provide your own {@link ParseRunner}.
     */
    public interface ParseRunnerProvider {

        /**
         * Retrieve the {@link ParseRunner} with the given rule.
         *
         * @param rule the root rule the runner will use.
         * @return an instance of a {@link ParseRunner}.
         * @throws NullPointerException if {@code rule} is {@code null}.
         */
        ParseRunner<Object> get(Rule rule);

    }

}
