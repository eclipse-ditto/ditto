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
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;

/**
 * Abstract implementation of the {@link DevOpsCommand} interface.
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
abstract class AbstractDevOpsCommand<T extends AbstractDevOpsCommand> extends AbstractCommand<T>
        implements DevOpsCommand<T> {

    @Nullable private final String serviceName;
    @Nullable private final Integer instance;

    /**
     * Constructs a new {@code AbstractDevOpsCommand} object.
     *
     * @param type the name of this command.
     * @param serviceName the service name to which to send the DevOpsCommand.
     * @param instance the instance index of the serviceName to which to send the DevOpsCommand.
     * @param dittoHeaders the headers of this command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected AbstractDevOpsCommand(final String type, @Nullable final String serviceName,
            @Nullable final Integer instance, final DittoHeaders dittoHeaders) {
        super(type, dittoHeaders);
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
        jsonObjectBuilder.set(DevOpsCommand.JsonFields.JSON_SERVICE_NAME, serviceName, predicate);
        jsonObjectBuilder.set(DevOpsCommand.JsonFields.JSON_INSTANCE, instance, predicate);
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
        final AbstractDevOpsCommand that = (AbstractDevOpsCommand) o;
        return that.canEqual(this) && Objects.equals(serviceName, that.serviceName)
                && Objects.equals(instance, that.instance) && super.equals(that);
    }

    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractDevOpsCommand;
    }

    @Override
    public String toString() {
        return "serviceName=" + serviceName + ", instance=" + instance + ", " + super.toString();
    }
}
