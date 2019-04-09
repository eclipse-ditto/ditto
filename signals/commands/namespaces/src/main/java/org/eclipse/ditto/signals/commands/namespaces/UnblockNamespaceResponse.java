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
package org.eclipse.ditto.signals.commands.namespaces;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to {@link UnblockNamespace}.
 */
@JsonParsableCommandResponse(type = UnblockNamespaceResponse.TYPE)
public final class UnblockNamespaceResponse extends AbstractNamespaceCommandResponse<UnblockNamespaceResponse> {

    /**
     * The type of the {@code UnblockNamespaceResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + UnblockNamespace.NAME;

    private UnblockNamespaceResponse(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        super(namespace, resourceType, TYPE, HttpStatusCode.OK, dittoHeaders);
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
    public static UnblockNamespaceResponse getInstance(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new UnblockNamespaceResponse(namespace, resourceType, dittoHeaders);
    }

    /**
     * Creates a new {@code UnblockNamespaceResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the UnblockNamespaceResponse is to be created.
     * @param headers the headers.
     * @return the deserialized response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * <ul>
     * <li>{@link NamespaceCommandResponse.JsonFields#NAMESPACE} or</li>
     * <li>{@link NamespaceCommandResponse.JsonFields#RESOURCE_TYPE}.</li>
     * </ul>
     */
    public static UnblockNamespaceResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return new CommandResponseJsonDeserializer<UnblockNamespaceResponse>(TYPE, jsonObject).deserialize(
                statusCode -> {
                    final String namespace = jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.NAMESPACE);
                    final String resourceType =
                            jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE);

                    return new UnblockNamespaceResponse(namespace, resourceType, headers);
                });
    }

    @Override
    public UnblockNamespaceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new UnblockNamespaceResponse(getNamespace(), getResourceType(), dittoHeaders);
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
