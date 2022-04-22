/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

/**
 * TODO TJ doc
 *
 * @param <S>
 * @param <R>
 */
public abstract class AbstractEnforcementReloaded<S extends Signal<?>, R extends CommandResponse<?>>
        implements EnforcementReloaded<S, R> {

    protected static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(AbstractEnforcementReloaded.class);

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}, it is returned as is
     * (without modification), otherwise it is wrapped inside a {@link GatewayInternalErrorException}.
     *
     * @param hint hint about the nature of the error.
     * @param throwable the error.
     * @param dittoHeaders the DittoHeaders to use for the DittoRuntimeException.
     * @return DittoRuntimeException suitable for transmission of the error.
     */
    protected DittoRuntimeException reportError(final String hint, @Nullable final Throwable throwable,
            final DittoHeaders dittoHeaders) {
        final Throwable error = throwable == null
                ? new NullPointerException("Result and error are both null")
                : throwable;
        final var dre = DittoRuntimeException.asDittoRuntimeException(
                error, cause -> reportUnexpectedError(hint, cause, dittoHeaders));
        LOGGER.withCorrelationId(dittoHeaders)
                .info("{} - {}: {}", hint, dre.getClass().getSimpleName(), dre.getMessage());
        return dre;
    }


    private DittoRuntimeException reportUnexpectedError(final String hint, final Throwable error,
            final DittoHeaders dittoHeaders) {
        LOGGER.withCorrelationId(dittoHeaders)
                .error("Unexpected error {} - {}: {}", hint, error.getClass().getSimpleName(),
                        error.getMessage(), error);

        // TODO TJ use other internal error exception!
        return GatewayInternalErrorException.newBuilder()
                .cause(error)
                .dittoHeaders(dittoHeaders)
                .build();
    }

}
