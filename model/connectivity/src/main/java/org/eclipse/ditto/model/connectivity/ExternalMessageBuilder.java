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
package org.eclipse.ditto.model.connectivity;

import java.nio.ByteBuffer;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Builder for building instances of {@link ExternalMessage}.
 */
public interface ExternalMessageBuilder {

    /**
     * Adds the additional header identified by the passed {@code key} and {@code value} to this builder.
     *
     * @param key the header key
     * @param value the header value
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withAdditionalHeaders(String key, String value);

    /**
     * Adds the additional headers to this builder.
     *
     * @param additionalHeaders the additional headers to add
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withAdditionalHeaders(Map<String, String> additionalHeaders);

    /**
     * Sets the message headers for this builder. Existing headers are replaced!
     *
     * @param headers the headers to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withHeaders(Map<String, String> headers);

    /**
     * Sets the passed {@code text} to the builder and also changing the
     * {@link org.eclipse.ditto.model.connectivity.ExternalMessage.PayloadType PayloadType} to {@code TEXT}.
     *
     * @param text the text payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withText(@Nullable String text);

    /**
     * Sets the passed {@code bytes} to the builder and also changing the
     * {@link org.eclipse.ditto.model.connectivity.ExternalMessage.PayloadType PayloadType} to {@code BYTES}.
     *
     * @param bytes the bytes payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withBytes(@Nullable byte[] bytes);

    /**
     * Sets the passed {@code bytes} to the builder and also changing the
     * {@link org.eclipse.ditto.model.connectivity.ExternalMessage.PayloadType PayloadType} to {@code BYTES}.
     *
     * @param bytes the bytes payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withBytes(@Nullable ByteBuffer bytes);

    /**
     * Associates an {@link AuthorizationContext} with the message.
     *
     * @param authorizationContext the {@link AuthorizationContext} assigned to the message
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withAuthorizationContext(AuthorizationContext authorizationContext);

    /**
     * Associates {@link ThingIdEnforcement} data with the message. Pass {@code null} to disable enforcement.
     *
     * @param thingIdEnforcement enforcement data
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withThingIdEnforcement(@Nullable ThingIdEnforcement thingIdEnforcement);

    /**
     * Marks the message as a response message.
     *
     * @param response whether the message is a response
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder asResponse(boolean response);

    /**
     * Marks the message as an error message.
     *
     * @param error whether the message is an error message
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder asError(boolean error);

    /**
     * Builds the ExternalMessage.
     *
     * @return the build message
     */
    ExternalMessage build();
}
