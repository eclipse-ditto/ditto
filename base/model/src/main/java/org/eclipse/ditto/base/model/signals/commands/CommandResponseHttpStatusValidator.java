/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;

/**
 * Validates if a particular {@link HttpStatus} is valid for a certain {@link CommandResponse}.
 *
 * @since 2.3.0
 */
@Immutable
public final class CommandResponseHttpStatusValidator {

    private CommandResponseHttpStatusValidator() {
        throw new AssertionError();
    }

    /**
     * Checks if the specified {@code HttpStatus} argument is contained in the also specified allowed HTTP statuses
     * argument.
     *
     * @param httpStatus the HTTP status to be validated.
     * @param allowedHttpStatuses the HTTP statuses that are allowed.
     * @param commandResponseType is required to build a descriptive error message in case validation failed.
     * @return {@code httpStatus} if it is valid.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code allowedHttpStatuses} is empty or if {@code httpStatus} is not in
     * {@code allowedHttpStatuses}.
     */
    public static HttpStatus validateHttpStatus(final HttpStatus httpStatus,
            final Collection<HttpStatus> allowedHttpStatuses,
            final Class<? extends CommandResponse> commandResponseType) {

        ConditionChecker.argumentNotEmpty(allowedHttpStatuses, "allowedHttpStatuses");
        checkNotNull(commandResponseType, "commandResponseType");
        return ConditionChecker.checkArgument(checkNotNull(httpStatus, "httpStatus"),
                allowedHttpStatuses::contains,
                () -> MessageFormat.format("{0} is invalid. {1} only allows {2}.",
                        httpStatus,
                        commandResponseType.getSimpleName(),
                        allowedHttpStatuses));
    }

}
