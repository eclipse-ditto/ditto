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

import java.util.Collections;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonObject;

/**
 * Response to {@link UnblockNamespace}.
 */
@JsonParsableCommandResponse(type = UnblockNamespaceResponse.TYPE)
public final class UnblockNamespaceResponse extends AbstractNamespaceCommandResponse<UnblockNamespaceResponse> {

    /**
     * The type of the {@code UnblockNamespaceResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + UnblockNamespace.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<UnblockNamespaceResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new UnblockNamespaceResponse(
                                jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.NAMESPACE),
                                jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private UnblockNamespaceResponse(final CharSequence namespace,
            final CharSequence resourceType,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(namespace,
                resourceType,
                TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        UnblockNamespaceResponse.class),
                dittoHeaders);
    }

    /**
     * Returns an instance of {@code UnblockNamespaceResponse}.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static UnblockNamespaceResponse getInstance(final CharSequence namespace,
            final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new UnblockNamespaceResponse(namespace, resourceType, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a new {@code UnblockNamespaceResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the UnblockNamespaceResponse is to be created.
     * @param dittoHeaders the dittoHeaders.
     * @return the deserialized response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * <ul>
     * <li>{@link org.eclipse.ditto.base.model.namespaces.signals.commands.NamespaceCommandResponse.JsonFields#NAMESPACE} or</li>
     * <li>{@link org.eclipse.ditto.base.model.namespaces.signals.commands.NamespaceCommandResponse.JsonFields#RESOURCE_TYPE}.</li>
     * </ul>
     */
    public static UnblockNamespaceResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public UnblockNamespaceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new UnblockNamespaceResponse(getNamespace(), getResourceType(), getHttpStatus(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof UnblockNamespaceResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
