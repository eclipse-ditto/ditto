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
package org.eclipse.ditto.model.base.headers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.TimeoutInvalidException;

/**
 * This validator parses a CharSequence to a {@link DittoDuration}.
 * If parsing fails, a {@link TimeoutInvalidException} is thrown.
 * @since 1.2.0
 */
@Immutable
final class TimeoutValueValidator extends AbstractHeaderValueValidator {

    private TimeoutValueValidator() {
        super(DittoDuration.class::equals);
    }

    static TimeoutValueValidator getInstance() {
        return new TimeoutValueValidator();
    }

    @Override
    protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
        try {
            DittoDuration.parseDuration(value);
        } catch (final IllegalArgumentException e) {
            throw TimeoutInvalidException.newBuilder(e.getMessage()).build();
        }
    }

}
