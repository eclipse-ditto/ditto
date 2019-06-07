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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * <p>
 * Abstract base implementation of common commands.
 * </p>
 * <p>
 * <em>Note: Implementations of this class are required to be immutable.</em>
 * </p>
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class CommonCommandResponse<T extends CommonCommandResponse> extends AbstractCommandResponse<T> {

    /**
     * Type prefix of namespace commands.
     */
    protected static final String TYPE_PREFIX = "common." + TYPE_QUALIFIER + ":";

    /**
     * The resource type of common commands.
     */
    protected static final String RESOURCE_TYPE = "common";

    /**
     * Constructs a new {@code CommonCommand} object.
     *
     * @param type the name of this command.
     * @param statusCode the status code of the response
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected CommonCommandResponse(final String type, final HttpStatusCode statusCode, final DittoHeaders dittoHeaders) {
        super(type, statusCode, dittoHeaders);
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.emptyPointer();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final CommonCommandResponse<?> that = (CommonCommandResponse<?>) o;
        return that.canEqual(this) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CommonCommandResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return super.toString();
    }

}
