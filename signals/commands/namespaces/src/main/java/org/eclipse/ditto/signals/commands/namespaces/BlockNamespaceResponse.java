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

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;

/**
 * Response to {@link BlockNamespace}.
 */
public final class BlockNamespaceResponse extends AbstractNamespaceCommandResponse<BlockNamespaceResponse> {

    /**
     * The type of the {@code BlockNamespaceResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + BlockNamespace.NAME;

    private BlockNamespaceResponse(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        super(namespace, resourceType, TYPE, HttpStatusCode.OK, dittoHeaders);
    }

    /**
     * Returns an instance of {@code BlockNamespaceResponse}.
     *
     * @param namespace the namespace the returned response relates to.
     * @param resourceType type of the {@code Resource} represented by the returned response.
     * @param dittoHeaders the headers of the command which caused the returned response.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} or {@code resourceType} is empty.
     */
    public static BlockNamespaceResponse getInstance(final CharSequence namespace, final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new BlockNamespaceResponse(namespace, resourceType, dittoHeaders);
    }

    /**
     * Creates a new {@code BlockNamespaceResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the BlockNamespaceResponse is to be created.
     * @param headers the headers.
     * @return the deserialized response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * <ul>
     *     <li>{@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponse.JsonFields#NAMESPACE} or</li>
     *     <li>{@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommandResponse.JsonFields#RESOURCE_TYPE}.</li>
     * </ul>
     */
    public static BlockNamespaceResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return new CommandResponseJsonDeserializer<BlockNamespaceResponse>(TYPE, jsonObject).deserialize(statusCode -> {
            final String namespace = jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.NAMESPACE);
            final String resourceType = jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE);

            return new BlockNamespaceResponse(namespace, resourceType, headers);
        });
    }

    @Override
    public BlockNamespaceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        if (Objects.equals(getDittoHeaders(), dittoHeaders)) {
            return this;
        }
        return new BlockNamespaceResponse(getNamespace(), getResourceType(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof BlockNamespaceResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
