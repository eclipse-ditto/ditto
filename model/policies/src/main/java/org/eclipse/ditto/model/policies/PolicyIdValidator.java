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
package org.eclipse.ditto.model.policies;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.EntityIdValidator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;

/**
 * Validates an ID against {@link Policy#ID_REGEX}. If the ID is invalid a {@link PolicyIdInvalidException} is thrown.
 */
@Immutable
public final class PolicyIdValidator extends EntityIdValidator {

    private PolicyIdValidator() { }

    /**
     * Returns a {@code PolicyIdValidator} instance.
     *
     * @return the PolicyIdValidator.
     */
    public static PolicyIdValidator getInstance() {
        return new PolicyIdValidator();
    }

    @Override
    protected DittoRuntimeExceptionBuilder createExceptionBuilder(@Nullable final CharSequence id) {
        return PolicyIdInvalidException.newBuilder(id);
    }

}
