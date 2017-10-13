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
package org.eclipse.ditto.signals.commands.live.base;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

/**
 * Base for factories of {@link org.eclipse.ditto.signals.commands.base.CommandResponse}s which create {@code
 * CommandResponse}s for incoming {@link org.eclipse.ditto.signals.commands.base.Command}s.
 */
@SuppressWarnings("squid:S1609")
public interface LiveCommandResponseFactory {

    /**
     * Creates a generic {@link ErrorResponse} which includes the passed {@link DittoRuntimeException}.
     * <p>
     * Use this method only if you are absolutely sure that the counterpart which issued the {@link
     * org.eclipse.ditto.signals.commands.base.Command} expects such a type of {@code ErrorResponse} for the issued
     * {@code Command}.
     *
     * @param dittoRuntimeException the DittoRuntimeException to include in the ErrorResponse.
     * @return the built ErrorResponse.
     * @throws NullPointerException if {@code DittoRuntimeException} is {@code null}.
     */
    default ThingErrorResponse errorResponse(final DittoRuntimeException dittoRuntimeException) {
        return ThingErrorResponse.of(dittoRuntimeException);
    }

    /**
     * Creates a generic {@link ErrorResponse} which includes the passed {@link DittoRuntimeException}.
     * <p>
     * Use this method only if you are absolutely sure that the counterpart which issued the {@link
     * org.eclipse.ditto.signals.commands.base.Command} expects such a type of {@code ErrorResponse} for the issued
     * {@code Command}.
     *
     * @param thingId the Thing ID of the related Thing.
     * @param dittoRuntimeException the DittoRuntimeException to include in the ErrorResponse.
     * @return the built ErrorResponse.
     * @throws NullPointerException if {@code dittoRuntimeException} is {@code null}.
     */
    default ThingErrorResponse errorResponse(final String thingId,
            final DittoRuntimeException dittoRuntimeException) {
        return ThingErrorResponse.of(thingId, dittoRuntimeException);
    }

}
