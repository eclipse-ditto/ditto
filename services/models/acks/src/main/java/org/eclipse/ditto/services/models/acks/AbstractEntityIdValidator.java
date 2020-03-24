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
package org.eclipse.ditto.services.models.acks;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.function.Consumer;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;

/**
 * Abstract base for validating whether given {@code EntityIdWithType} instances are equal to an {@code expected}
 * instance.
 *
 * @param <I> the type of the EntityIdWithType to validate
 * @since 1.1.0
 */
@Immutable
abstract class AbstractEntityIdValidator<I extends EntityIdWithType> implements Consumer<I> {

    private final I expected;

    /**
     * Constructs a new EntityIdValidator object.
     */
    protected AbstractEntityIdValidator(final I expected) {
        this.expected = checkNotNull(expected, "expected");
    }

    @Override
    public void accept(final EntityIdWithType actual) {
        if (!areEqual(actual, expected)) {
            final String ptrn = "The received Acknowledgement''s entity ID <{0}> differs from the expected <{1}>!";
            throw new IllegalArgumentException(MessageFormat.format(ptrn, actual, expected));
        }
    }

    /**
     * Indicates whether the two given entity IDs are regarded as being equal.
     *
     * @param actual the entity ID of a received Acknowledgement.
     * @param expected the entity ID all entity IDs of received Acknowledgements are supposed to be equal to.
     * @return {@code true} if the given {@code actual} and {@code expected} are regarded as being equal,
     * {@code false} else.
     */
    protected boolean areEqual(final EntityIdWithType actual, final I expected) {
        return actual.equals(expected);
    }

}
