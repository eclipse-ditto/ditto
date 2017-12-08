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
package org.eclipse.ditto.signals.commands.devops;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Abstract implementation of the {@link DevOpsCommandResponse} interface.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
abstract class AbstractDevOpsCommandResponse<T extends AbstractDevOpsCommandResponse> extends AbstractCommandResponse<T>
        implements DevOpsCommandResponse<T> {

    @Nullable private final String serviceName;
    @Nullable private final Integer instance;

    /**
     * Constructs a new {@code AbstractDevOpsCommandResponse} object.
     *
     * @param serviceName the service name from which the DevOpsCommandResponse originated.
     * @param instance the instance index of the serviceName from which the DevOpsCommandResponse originated.
     * @param responseType the name of this command response.
     * @param statusCode the status code of this command response.
     * @param dittoHeaders the headers of this command response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractDevOpsCommandResponse(final String responseType, @Nullable final String serviceName,
            @Nullable final Integer instance, final HttpStatusCode statusCode, final DittoHeaders dittoHeaders) {
        super(responseType, statusCode, dittoHeaders);
        this.serviceName = serviceName;
        this.instance = instance;
    }

    public Optional<String> getServiceName() {
        return Optional.ofNullable(serviceName);
    }

    public Optional<Integer> getInstance() {
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
        return Objects.hash(super.hashCode());
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
        final AbstractDevOpsCommandResponse that = (AbstractDevOpsCommandResponse) o;
        return that.canEqual(this) && super.equals(that);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractDevOpsCommandResponse;
    }

    @Override
    public String toString() {
        return "serviceName=" + serviceName + ", instance=" + instance + ", " + super.toString();
    }
}
