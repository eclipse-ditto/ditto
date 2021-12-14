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
 * Response to {@link BlockNamespace}.
 */
@JsonParsableCommandResponse(type = BlockNamespaceResponse.TYPE)
public final class BlockNamespaceResponse extends AbstractNamespaceCommandResponse<BlockNamespaceResponse> {

    /**
     * The type of the {@code BlockNamespaceResponse}.
     */
    public static final String TYPE = TYPE_PREFIX + BlockNamespace.NAME;

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<BlockNamespaceResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        return new BlockNamespaceResponse(
                                jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.NAMESPACE),
                                jsonObject.getValueOrThrow(NamespaceCommandResponse.JsonFields.RESOURCE_TYPE),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private BlockNamespaceResponse(final CharSequence namespace,
            final CharSequence resourceType,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(namespace,
                resourceType,
                TYPE,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        BlockNamespaceResponse.class),
                dittoHeaders);
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
    public static BlockNamespaceResponse getInstance(final CharSequence namespace,
            final CharSequence resourceType,
            final DittoHeaders dittoHeaders) {

        return new BlockNamespaceResponse(namespace, resourceType, HTTP_STATUS, dittoHeaders);
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
     * <li>{@link org.eclipse.ditto.base.model.namespaces.signals.commands.NamespaceCommandResponse.JsonFields#NAMESPACE} or</li>
     * <li>{@link org.eclipse.ditto.base.model.namespaces.signals.commands.NamespaceCommandResponse.JsonFields#RESOURCE_TYPE}.</li>
     * </ul>
     */
    public static BlockNamespaceResponse fromJson(final JsonObject jsonObject, final DittoHeaders headers) {
        return JSON_DESERIALIZER.deserialize(jsonObject, headers);
    }

    @Override
    public BlockNamespaceResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new BlockNamespaceResponse(getNamespace(), getResourceType(), getHttpStatus(), dittoHeaders);
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
