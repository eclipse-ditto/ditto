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
package org.eclipse.ditto.rql.parser.thingsearch;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.rql.parser.thingsearch.internal.RqlOptionParser$;
import org.eclipse.ditto.thingsearch.model.Option;

/**
 * RQL Parser parsing options in the RQL "standard" according to https://github.com/persvr/rql.
 */
public final class RqlOptionParser implements OptionParser {

    /**
     * Delimiter between options in their string representation.
     */
    private static final String DELIMITER = ",";

    private static final OptionParser PARSER = RqlOptionParser$.MODULE$;

    @Override
    public List<Option> parse(final String input) {
        return parseOptions(input);
    }

    /**
     * Parse a string as a list of options.
     *
     * @param input the string.
     * @return the list of options.
     */
    public static List<Option> parseOptions(final String input) {
        return PARSER.parse(input);
    }

    /**
     * Serialize a list of options to a string that parses to the same list of options.
     *
     * @param options the list of options.
     * @return the serialized string.
     */
    public static String unparse(final List<Option> options) {
        // join the option as delimited string.
        // RQL option grammar's inverse logic exists in Option.toString and is not generated from the parboiled grammar.
        return options.stream()
                .map(Option::toString)
                .collect(Collectors.joining(DELIMITER));
    }
}
