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
package org.eclipse.ditto.placeholders.filter;

/**
 * Represents a function that can be used for filtering mechanisms.
 */
public interface FilterFunction {

    /**
     * @return the name of the function.
     */
    String getName();

    /**
     * Indicates whether an element should be filtered or not. If this method returns true, a value will be kept. If
     * not the value will be dropped.
     *
     * @param parameters the parameters on which the decision should be made.
     * @return true if condition succeeds, false if not.
     */
    boolean apply(String... parameters);

}
