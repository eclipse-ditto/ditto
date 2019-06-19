/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
 * Abstract base implementation for responses to {@link CommonCommand}s.
 *
 * @param <T> the type of the implementing class.
 */
public abstract class CommonCommandResponse<T extends AbstractCommandResponse> extends AbstractCommandResponse<T> {
    /**
     * Type prefix.
     */
    protected static final String TYPE_PREFIX = "common." + TYPE_QUALIFIER + ":";

    /**
     * Resource type.
     */
    protected static final String RESOURCE_TYPE = "common";

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
        return RESOURCE_TYPE;
    }
}
