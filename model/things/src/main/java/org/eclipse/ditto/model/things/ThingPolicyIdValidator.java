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
package org.eclipse.ditto.model.things;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.EntityIdValidator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;

/**
 * Validates the id of a policy.
 * If the ID is invalid a {@link ThingPolicyIdInvalidException} is thrown.
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
