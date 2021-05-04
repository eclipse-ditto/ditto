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
package org.eclipse.ditto.base.model.headers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.exceptions.TimeoutInvalidException;

/**
 * This validator parses a CharSequence to a {@link org.eclipse.ditto.base.model.common.DittoDuration}.
 * If parsing fails, a {@link org.eclipse.ditto.base.model.exceptions.TimeoutInvalidException} is thrown.
 *
 * @since 1.2.0
 */
@Immutable
final class TimeoutValueValidator extends AbstractHeaderValueValidator {

    private static final TimeoutValueValidator INSTANCE = new TimeoutValueValidator();

    private TimeoutValueValidator() {
        super(DittoDuration.class::equals);
    }

    static TimeoutValueValidator getInstance() {
        return INSTANCE;
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
