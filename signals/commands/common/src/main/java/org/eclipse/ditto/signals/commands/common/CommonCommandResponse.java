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
package org.eclipse.ditto.signals.commands.common;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Super class of common command responses.
 *
 * @param <T> concrete class of the command.
 */
public abstract class CommonCommandResponse<T extends CommonCommandResponse> extends AbstractCommandResponse<T> {

    /**
     * Type prefix of common command responses.
     */
    public static final String TYPE_PREFIX = "common." + TYPE_QUALIFIER + ":";

    /**
     * Constructs a new {@code AbstractCommandResponse} object.
     *
     * @param responseType the type of this response.
     * @param statusCode the HTTP statusCode of this response.
     * @param dittoHeaders the headers of the CommandType which caused this CommandResponseType.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected CommonCommandResponse(final String responseType,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {
        super(responseType, statusCode, dittoHeaders);
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return CommonCommand.RESOURCE_TYPE;
    }
}
