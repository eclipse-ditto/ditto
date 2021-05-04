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
package org.eclipse.ditto.base.api.devops.signals.commands;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;

/**
 * Abstract implementation of the {@link DevOpsCommandResponse} interface.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
abstract class AbstractDevOpsCommandResponse<T extends AbstractDevOpsCommandResponse<T>> extends AbstractCommandResponse<T>
        implements DevOpsCommandResponse<T> {

    @Nullable private final String serviceName;
    @Nullable private final String instance;

    /**
     * Constructs a new {@code AbstractDevOpsCommandResponse} object.
     *
     * @param serviceName the service name from which the DevOpsCommandResponse originated.
     * @param instance the instance identifier of the serviceName from which the DevOpsCommandResponse originated.
     * @param responseType the name of this command response.
     * @param httpStatus the HTTP status of this command response.
     * @param dittoHeaders the headers of this command response.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 2.0.0
     */
    protected AbstractDevOpsCommandResponse(final String responseType,
            @Nullable final String serviceName,
            @Nullable final String instance,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(responseType, httpStatus, dittoHeaders);
        this.serviceName = serviceName;
        this.instance = instance;
    }

    @Override
    public Optional<String> getServiceName() {
        return Optional.ofNullable(serviceName);
    }

    @Override
    public Optional<String> getInstance() {
        return Optional.ofNullable(instance);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME, serviceName, predicate);
        jsonObjectBuilder.set(DevOpsCommandResponse.JsonFields.JSON_INSTANCE, instance, predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), serviceName, instance);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractDevOpsCommandResponse<?> that = (AbstractDevOpsCommandResponse<?>) o;
        return that.canEqual(this) && Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(instance, that.instance) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractDevOpsCommandResponse;
    }

    @Override
    public String toString() {
        return "serviceName=" + serviceName + ", instance=" + instance + ", " + super.toString();
    }
}
