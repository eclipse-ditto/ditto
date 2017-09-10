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
package org.eclipse.ditto.model.base.common;

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
