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
package org.eclipse.ditto.model.placeholders;

import java.util.List;

/**
 * An expression stage containing a {@code prefix} and a {@code name}, separated by a {@value #SEPARATOR}.
 */
public interface ExpressionStage {

    /**
     * The constant SEPARATOR.
     */
    String SEPARATOR = ":";

    /**
     * The part of the expression variable before ':'.
     *
     * @return the prefix.
     */
    String getPrefix();

    /**
     * Retrieves the supported names this expression can substitute.
     *
     * @return the supported names this expression can substitute.
     */
    List<String> getSupportedNames();

    /**
     * Test whether a expression name (i. e., the part after ':') is supported.
     *
     * @param name the expression name.
     * @return whether the expression name is supported.
     */
    boolean supports(String name);
}
