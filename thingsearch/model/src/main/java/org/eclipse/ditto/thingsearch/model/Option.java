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
package org.eclipse.ditto.thingsearch.model;

import javax.annotation.concurrent.Immutable;

/**
 * Common interface for all options which can be applied to a search query.
 */
@Immutable
public interface Option {

    /**
     * PredicateVisitor Pattern. Takes a visitor as parameter and calls the corresponding visit method with itself as
     * parameter.
     *
     * @param visitor the visitor which should be called.
     * @throws NullPointerException if {@code visitor} is {@code null}.
     */
    void accept(OptionVisitor visitor);

    /**
     * Returns the string representation of this option. This string complies to a particular format which is defined
     * by each implementation of this interface.
     *
     * @return the particular string representation of this option.
     */
    @Override
    String toString();

}
