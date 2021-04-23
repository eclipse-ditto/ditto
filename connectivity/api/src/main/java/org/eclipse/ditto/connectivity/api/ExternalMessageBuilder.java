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
package org.eclipse.ditto.connectivity.api;

import java.nio.ByteBuffer;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.Signal;

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
     * {@link ExternalMessage.PayloadType PayloadType} to {@code TEXT}.
     * NOT for use in consumer actors! They should set both the text and the byte payload.
     *
     * @param text the text payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withText(@Nullable String text);

    /**
     * Sets the passed {@code bytes} to the builder and also changing the
     * {@link ExternalMessage.PayloadType PayloadType} to {@code BYTES}.
     *
     * @param bytes the bytes payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withBytes(@Nullable byte[] bytes);

    /**
     * Sets the passed {@code bytes} to the builder and also changing the
     * {@link ExternalMessage.PayloadType PayloadType} to {@code BYTES}.
     *
     * @param bytes the bytes payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withBytes(@Nullable ByteBuffer bytes);

    /**
     * Sets the passed {@code text} and {@code bytes} to the builder and also changing the
     * {@link ExternalMessage.PayloadType PayloadType} to {@code TEXT_AND_BYTES}.
     *
     * @param text the text payload to set
     * @param bytes the bytes payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withTextAndBytes(@Nullable String text, @Nullable byte[] bytes);

    /**
     * Sets the passed {@code text} and {@code bytes} to the builder and also changing the
     * {@link ExternalMessage.PayloadType PayloadType} to {@code TEXT_AND_BYTES}.
     *
     * @param text the text payload to set
     * @param bytes the bytes payload to set
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withTextAndBytes(@Nullable String text, @Nullable ByteBuffer bytes);

    /**
     * Associates an {@link AuthorizationContext} with the message.
     *
     * @param authorizationContext the {@link AuthorizationContext} assigned to the message
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withAuthorizationContext(AuthorizationContext authorizationContext);

    /**
     * Associates an {@link TopicPath} with the message.
     *
     * @param topicPath the {@link TopicPath} assigned to the message
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withTopicPath(TopicPath topicPath);

    /**
     * Associates {@link Enforcement} data with the message. Pass {@code null} to disable enforcement.
     *
     * @param <F> the {@link EnforcementFilter} type
     * @param enforcement enforcement data
     * @return this builder in order to enable method chaining
     */
    <F extends EnforcementFilter<Signal<?>>> ExternalMessageBuilder withEnforcement(@Nullable F enforcement);

    /**
     * Associates {@link HeaderMapping} data with the message. Pass {@code null} to disable headerMapping.
     *
     * @param headerMapping the mappings
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withHeaderMapping(@Nullable HeaderMapping headerMapping);

    /**
     * Adds the source address to this message, where this messages was received.
     *
     * @param sourceAddress the source address
     * @return this builder in order to enable method chaining
     */
    ExternalMessageBuilder withSourceAddress(@Nullable String sourceAddress);

    /**
     * Adds the source to this message, where this messages was received.
     *
     * @param source the source
     * @return this builder in order to enable method chaining
     * @since 1.2.0
     */
    ExternalMessageBuilder withSource(@Nullable Source source);

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
     * Attach headers of the signal that created the external message for generation of errors to send back
     * into the Ditto cluster.
     *
     * @param internalHeaders headers of the signal.
     * @return this builder.
     */
    ExternalMessageBuilder withInternalHeaders(DittoHeaders internalHeaders);

    /**
     * Defines which mappings are applied for this {@link ExternalMessage}.
     *
     * @param payloadMapping the payloadMapping that is applied for this message
     * @return this builder.
     */
    ExternalMessageBuilder withPayloadMapping(PayloadMapping payloadMapping);

    /**
     * Builds the ExternalMessage.
     *
     * @return the build message
     */
    ExternalMessage build();
}
