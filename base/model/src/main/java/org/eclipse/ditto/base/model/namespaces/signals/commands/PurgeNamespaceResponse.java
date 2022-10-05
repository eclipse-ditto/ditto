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
package org.eclipse.ditto.base.model.namespaces.signals.commands;

import java.util.Arrays;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonObject;

/**
 * Response to {@link PurgeNamespace} for speeding up namespace purge.
 */
@Immutable
@JsonParsableCommandResponse(type = PurgeNamespaceResponse.TYPE)
public final class PurgeNamespaceResponse extends AbstractNamespaceCommandResponse<PurgeNamespaceResponse> {

    /**
     * The type of the {@code PurgeNamespaceResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + PurgeNamespace.NAME;

    private static final CommandResponseJsonDeserializer<PurgeNamespaceResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new PurgeNamespaceResponse(
                                jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.NAMESPACE),
                                jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private PurgeNamespaceResponse(final CharSequence namespace,
            final CharSequence resourceType,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(namespace,
                resourceType,
                TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Arrays.asList(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR),
                        PurgeNamespaceResponse.class),
                dittoHeaders);
    }

    /**
     * Returns an instance of {@code PurgeNamespaceResponse} which indicates a successful namespace purge.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a successful namespace purge.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static PurgeNamespaceResponse successful(final CharSequence namespace,
            final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new PurgeNamespaceResponse(namespace, resourceType, HttpStatus.OK, dittoHeaders);
    }

    /**
     * Returns an instance of {@code PurgeNamespaceResponse} which indicates that a namespace purge failed.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return a response for a failed namespace purge.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static PurgeNamespaceResponse failed(final CharSequence namespace,
            final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new PurgeNamespaceResponse(namespace, resourceType, HttpStatus.INTERNAL_SERVER_ERROR, dittoHeaders);
    }

    /**
     * Creates a new {@code PurgeNamespaceResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the PurgeNamespaceResponse is to be created.
     * @param dittoHeaders the dittoHeaders.
     * @return the deserialized response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * <ul>
     *     <li>{@link NamespaceCommandResponse.JsonFields#NAMESPACE},</li>
     *     <li>{@link NamespaceCommandResponse.JsonFields#RESOURCE_TYPE} or</li>
     * </ul>
     */
    public static PurgeNamespaceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Indicates whether the namespace was purged successfully.
     *
     * @return {@code true} if the namespace was purged, {@code false} else.
     */
    public boolean isSuccessful() {
        final HttpStatus httpStatus = getHttpStatus();
        return httpStatus.isSuccess();
    }

    @Override
    public PurgeNamespaceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        if (Objects.equals(getDittoHeaders(), dittoHeaders)) {
            return this;
        }
        return new PurgeNamespaceResponse(getNamespace(), getResourceType(), getHttpStatus(), dittoHeaders);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final PurgeNamespaceResponse that = (PurgeNamespaceResponse) o;
        return that.canEqual(this) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof PurgeNamespaceResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", successful=" + isSuccessful() + "]";
    }

}
