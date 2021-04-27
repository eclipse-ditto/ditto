/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.common;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;

/**
 * Abstract base implementation for responses to {@link CommonCommand}s.
 *
 * @param <T> the type of the implementing class.
 */
public abstract class CommonCommandResponse<T extends CommonCommandResponse<T>> extends AbstractCommandResponse<T> {

    /**
     * Type prefix.
     */
    protected static final String TYPE_PREFIX = "common." + TYPE_QUALIFIER + ":";

    /**
     * Resource type.
     */
    protected static final String RESOURCE_TYPE = "common";

    /**
     * Constructs a new {@code AbstractCommandResponse} object.
     *
     * @param responseType the type of this response.
     * @param httpStatus the HTTP status of this response.
     * @param dittoHeaders the headers of the CommandType which caused this CommandResponseType.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected CommonCommandResponse(final String responseType, final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(responseType, httpStatus, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

}
