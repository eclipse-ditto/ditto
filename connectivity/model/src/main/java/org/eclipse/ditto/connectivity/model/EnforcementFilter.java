/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

/**
 * Validate a given input of type <code>M</code>.
 *
 * @param <M> the type of elements that can be validated by this filter.
 */
public interface EnforcementFilter<M> {

    /**
     * Validates the given input. Throws an exception if the input is not valid.
     *
     * @param filterInput the input that should be validated.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if input was invalid.
     */
    void match(M filterInput);
}
