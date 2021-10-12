/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders;

import java.util.List;

/**
 * An expression containing a {@code prefix} and a {@code name}, separated by a {@value #SEPARATOR}.
 */
public interface Expression {

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
