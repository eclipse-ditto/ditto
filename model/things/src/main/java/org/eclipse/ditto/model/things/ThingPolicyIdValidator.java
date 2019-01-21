/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.things;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.EntityIdValidator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;

/**
 * Validates the id of a policy.
 * If the ID is invalid a {@link org.eclipse.ditto.model.things.ThingPolicyIdInvalidException} is thrown.
 */
@Immutable
public final class ThingPolicyIdValidator extends EntityIdValidator {

    private ThingPolicyIdValidator() { }

    /**
     * Returns a {@code ThingIdValidator} instance.
     *
     * @return the ThingIdValidator.
     */
    public static ThingPolicyIdValidator getInstance() {
        return new ThingPolicyIdValidator();
    }

    @Override
    protected DittoRuntimeExceptionBuilder createExceptionBuilder(final CharSequence id) {
        return ThingPolicyIdInvalidException.newBuilder(id);
    }

}
