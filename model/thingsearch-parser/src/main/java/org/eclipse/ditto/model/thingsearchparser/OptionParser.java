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
package org.eclipse.ditto.model.thingsearchparser;

import java.util.List;

import org.eclipse.ditto.model.thingsearch.Option;

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
