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

import org.eclipse.ditto.thingsearch.model.Option;

/**
 * Interface a option parser has to implement.
 */
public interface OptionParser {

    /**
     * Parse the specified input.
     *
     * @param input the input that should be parsed.
     * @return a list of {@link Option}s.
     */
    List<Option> parse(String input);

}
