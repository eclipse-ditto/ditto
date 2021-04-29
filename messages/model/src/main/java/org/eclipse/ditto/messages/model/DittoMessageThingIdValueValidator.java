/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.messages.model;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.AbstractHeaderValueValidator;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingIdInvalidException;

/**
 * This validator checks if a CharSequence is a valid {@link ThingId}.
 * If validation fails, a {@link org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException} is thrown.
 */
@Immutable
final class DittoMessageThingIdValueValidator extends AbstractHeaderValueValidator {

    private static final DittoMessageThingIdValueValidator INSTANCE = new DittoMessageThingIdValueValidator();

    private DittoMessageThingIdValueValidator() {
        super(String.class::equals);
    }

    /**
     * Returns an instance of {@code DittoMessageSubjectValueValidator}.
     *
     * @return the instance.
     */
    static DittoMessageThingIdValueValidator getInstance() {
        return INSTANCE;
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            ThingId.of(value.toString());
        } catch (final ThingIdInvalidException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(definition, value, "thing ID")
                    .cause(e)
                    .build();
        }
    }

}
