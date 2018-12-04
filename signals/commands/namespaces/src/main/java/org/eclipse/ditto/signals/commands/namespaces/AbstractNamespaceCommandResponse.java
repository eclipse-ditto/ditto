/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.namespaces;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;

/**
 * Common base implementation of {@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponse}.
 */
@Immutable
abstract class AbstractNamespaceCommandResponse<T extends AbstractNamespaceCommandResponse>
        extends AbstractCommandResponse<T> implements NamespaceCommandResponse<T> {

    /**
     * Type prefix of NamespaceCommand responses.
     */
    protected static final String TYPE_PREFIX = "namespaces." + TYPE_QUALIFIER + ":";

    private final String namespace;
    private final String resourceType;

    /**
     * Constructs a new {@code AbstractNamespaceCommandResponse} object.
     *
     * @param namespace the namespace this response relates to.
     * @param resourceType type of the {@code Resource} represented by this response.
     * @param responseType the type of this response.
     * @param statusCode the HTTP statusCode of this response.
     * @param dittoHeaders the headers of the command which caused this response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    protected AbstractNamespaceCommandResponse(final CharSequence namespace,
            final CharSequence resourceType,
            final String responseType,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        super(responseType, statusCode, dittoHeaders);
        this.namespace = argumentNotEmpty(namespace, "namespace").toString();
        this.resourceType = argumentNotEmpty(resourceType, "resourceType").toString();
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    /**
     * Same as get {@link #getNamespace()}.
     */
    @Override
    public String getId() {
        return getNamespace();
    }

    @Override
    public String getResourceType() {
        return resourceType;
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
        final AbstractNamespaceCommandResponse<?> that = (AbstractNamespaceCommandResponse<?>) o;
        return that.canEqual(this) &&
                Objects.equals(namespace, that.namespace) &&
                Objects.equals(resourceType, that.resourceType) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractNamespaceCommandResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespace, resourceType);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        final Predicate<JsonField> extendedPredicate = schemaVersion.and(predicate);
        jsonObjectBuilder.set(NamespaceCommandResponse.JsonFields.NAMESPACE, getNamespace(), extendedPredicate);
        jsonObjectBuilder.set(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE, getResourceType(), extendedPredicate);
    }

    @Override
    public String toString() {
        return "namespace=" + namespace + ", resourceType=" + resourceType;
    }

}
