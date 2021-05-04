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
package org.eclipse.ditto.base.model.common;

import java.util.Optional;

/**
 * This interface represents a general purpose validator.
 */
public interface Validator {

    /**
     * Indicates the validation result.
     *
     * @return {@code true} if the validation was successful, {@code false} if the validation failed for some reason.
     * @see #getReason()
     */
    boolean isValid();

    /**
     * Returns the reason why the validation failed. The returned Optional only contains a reason if
     * {@link #isValid()} evaluates to {@code false}.
     *
     * @return the reason for validation failure.
     */
    Optional<String> getReason();

}
