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

package org.eclipse.ditto.model.base.entity.validation;

import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Validator;

/**
 * This interface represents a general pattern validator.
 */
@Immutable
public interface PatternValidator extends Validator {

    /**
     * Indicates the validation result.
     *
     * @return {@code true} if the validation was successful, {@code false} if the validation failed for some reason.
     */
    boolean isValid(CharSequence toBeValidated);

    /**
     * Returns a pattern.
     *
     * @return {@code Pattern}.
     */

    @Nullable
    Pattern getPattern();

}
