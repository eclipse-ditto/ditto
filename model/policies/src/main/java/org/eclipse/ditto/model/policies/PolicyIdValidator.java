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
package org.eclipse.ditto.model.policies;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.AbstractIdValidator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;

/**
 * Validates an ID against {@link Policy#ID_REGEX}. If the ID is invalid a {@link PolicyIdInvalidException} is thrown.
 */
@Immutable
public final class PolicyIdValidator extends AbstractIdValidator {

    private PolicyIdValidator() {
        super(Policy.ID_REGEX);
    }

    /**
     * Returns a {@code PolicyIdValidator} instance.
     *
     * @return the PolicyIdValidator.
     */
    public static PolicyIdValidator getInstance() {
        return new PolicyIdValidator();
    }

    @Override
    protected DittoRuntimeExceptionBuilder createBuilder(@Nullable final CharSequence id) {
        return PolicyIdInvalidException.newBuilder(id);
    }

}
