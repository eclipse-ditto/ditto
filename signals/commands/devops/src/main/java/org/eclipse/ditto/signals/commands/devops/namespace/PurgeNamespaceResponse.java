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
package org.eclipse.ditto.signals.commands.devops.namespace;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommandResponse;

/**
 * Response to speed up namespace purge.
 * TODO: test this.
 */
@Immutable
public final class PurgeNamespaceResponse
        extends AbstractCommandResponse<PurgeNamespaceResponse>
        implements DevOpsCommandResponse<PurgeNamespaceResponse> {

    /**
     * Type of the command response.
     */
    public static final String TYPE = DevOpsCommandResponse.TYPE_PREFIX + PurgeNamespace.NAME;

    private final String resourceType;
    private final String namespace;
    private final boolean isSuccessful;

    private PurgeNamespaceResponse(final Builder builder) {

        super(TYPE, HttpStatusCode.OK, builder.dittoHeaders);
        this.resourceType = checkNotNull(builder.resourceType, "resourceType");
        this.namespace = checkNotNull(builder.namespace, "namespace");
        this.isSuccessful = builder.isSuccessful;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Convert this object to a builder.
     *
     * @return this object as a builder.
     */
    public Builder toBuilder() {
        return newBuilder()
                .dittoHeaders(getDittoHeaders())
                .resourceType(resourceType)
                .namespace(namespace)
                .setSuccessful(isSuccessful);
    }

    /**
     * @return resource type
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * @return whether the namespace has no data.
     */
    public boolean isSuccessful() {
        return isSuccessful;
    }

    public boolean isNotEmpty() {
        return !isSuccessful;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JsonFields.RESOURCE_TYPE, resourceType)
                .set(JsonFields.NAMESPACE, namespace)
                .set(JsonFields.IS_SUCCESSFUL, isSuccessful);
    }

    @Override
    public Optional<String> getServiceName() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getInstance() {
        return Optional.empty();
    }

    @Override
    public PurgeNamespaceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return toBuilder().dittoHeaders(dittoHeaders).build();
    }

    @Override
    public boolean equals(final Object o) {
        final Class<? extends PurgeNamespaceResponse> clazz = getClass();
        if (clazz.isInstance(o)) {
            final PurgeNamespaceResponse that = clazz.cast(o);
            return Objects.equals(resourceType, that.resourceType) &&
                    Objects.equals(namespace, that.namespace) &&
                    isSuccessful == that.isSuccessful &&
                    super.equals(that);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourceType, namespace, isSuccessful);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", resourceType=" + resourceType +
                ", namespace=" + namespace +
                ", isSuccessful=" + isSuccessful +
                "]";
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject this response in JSON format.
     * @param headers Ditto headers.
     * @return the deserialized response.
     */
    public static PurgeNamespaceResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return newBuilder()
                .dittoHeaders(headers)
                .resourceType(jsonObject.getValueOrThrow(JsonFields.RESOURCE_TYPE))
                .namespace(jsonObject.getValueOrThrow(JsonFields.NAMESPACE))
                .setSuccessful(jsonObject.getValueOrThrow(JsonFields.IS_SUCCESSFUL))
                .build();
    }

    /**
     * Builder for the command response.
     */
    public static final class Builder {

        @Nullable private String resourceType;
        @Nullable private String namespace;
        private boolean isSuccessful;
        private DittoHeaders dittoHeaders = DittoHeaders.empty();

        /**
         * Set the ditto headers.
         *
         * @param dittoHeaders the ditto headers.
         * @return this builder.
         */
        public Builder dittoHeaders(final DittoHeaders dittoHeaders) {
            this.dittoHeaders = dittoHeaders;
            return this;
        }

        /**
         * Set the resource type.
         *
         * @param resourceType the resource type.
         * @return this builder.
         */
        public Builder resourceType(@Nullable final String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        /**
         * Set the namespace.
         *
         * @param namespace the namespace.
         * @return this builder.
         */
        public Builder namespace(@Nullable final String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Set whether the namespace is empty.
         */
        public Builder setSuccessful(final boolean isEmpty) {
            this.isSuccessful = isEmpty;
            return this;
        }

        public PurgeNamespaceResponse build() {
            return new PurgeNamespaceResponse(this);
        }
    }

    /**
     * JSON fields.
     */
    public static final class JsonFields {

        /**
         * Resource type checked.
         */
        public static final JsonFieldDefinition<String> RESOURCE_TYPE =
                JsonFactory.newStringFieldDefinition("resourceType");

        /**
         * Namespace checked.
         */
        public static final JsonFieldDefinition<String> NAMESPACE =
                JsonFactory.newStringFieldDefinition("namespace");

        /**
         * Whether the namespace is empty.
         */
        public static final JsonFieldDefinition<Boolean> IS_SUCCESSFUL =
                JsonFactory.newBooleanFieldDefinition("isSuccessful");
    }
}
