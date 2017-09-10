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
package org.eclipse.ditto.model.thingsearch;

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
