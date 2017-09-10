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
package org.eclipse.ditto.model.things;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.AbstractIdValidator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;

/**
 * Validates an ID against {@link Thing#ID_REGEX}. If the ID is invalid a {@link ThingIdInvalidException} is thrown.
 */
@Immutable
public final class ThingIdValidator extends AbstractIdValidator {

    private ThingIdValidator() {
        super(Thing.ID_REGEX);
    }

    /**
     * Returns a {@code ThingIdValidator} instance.
     *
     * @return the ThingIdValidator.
     */
    public static ThingIdValidator getInstance() {
        return new ThingIdValidator();
    }

    @Override
    protected DittoRuntimeExceptionBuilder createBuilder(final CharSequence id) {
        return ThingIdInvalidException.newBuilder(id);
    }

}
