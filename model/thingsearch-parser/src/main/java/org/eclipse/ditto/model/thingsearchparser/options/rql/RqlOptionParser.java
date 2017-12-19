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
import org.eclipse.ditto.model.thingsearchparser.parser.RqlOptionParser$;

/**
 * RQL Parser parsing options in the RQL "standard" according to https://github.com/persvr/rql.
 */
public final class RqlOptionParser implements OptionParser {

    private static final OptionParser PARSER = RqlOptionParser$.MODULE$;

    @Override
    public List<Option> parse(final String input) {
        return PARSER.parse(input);
    }
}
