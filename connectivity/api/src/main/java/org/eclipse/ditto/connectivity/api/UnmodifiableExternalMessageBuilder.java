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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Mutable builder for building new instances of ExternalMessage.
 */
final class UnmodifiableExternalMessageBuilder implements ExternalMessageBuilder {

    private Map<String, String> headers;
    private boolean response = false;
    private boolean error = false;
    private ExternalMessage.PayloadType payloadType = ExternalMessage.PayloadType.UNKNOWN;
    @Nullable private String textPayload;
    @Nullable private ByteBuffer bytePayload;
    @Nullable private AuthorizationContext authorizationContext;
    @Nullable private TopicPath topicPath;
    @Nullable private EnforcementFilter<Signal<?>> enforcementFilter;
    @Nullable private HeaderMapping headerMapping;
    @Nullable private PayloadMapping payloadMapping;
    @Nullable private String sourceAddress;
    @Nullable private Source source;
    private DittoHeaders internalHeaders = DittoHeaders.empty();

    /**
     * Constructs a new MutableExternalMessageBuilder initialized with the passed {@code message}.
     *
     * @param message the ExternalMessage to use for initialization.
     */
    UnmodifiableExternalMessageBuilder(final ExternalMessage message) {
        this.headers = new HashMap<>(message.getHeaders());
        this.bytePayload = message.getBytePayload().orElse(null);
        this.textPayload = message.getTextPayload().orElse(null);
        this.payloadType = message.getPayloadType();
        this.response = message.isResponse();
        this.error = message.isError();
        this.authorizationContext = message.getAuthorizationContext().orElse(null);
        this.topicPath = message.getTopicPath().orElse(null);
        this.enforcementFilter = message.getEnforcementFilter().orElse(null);
        this.headerMapping = message.getHeaderMapping().orElse(null);
        this.payloadMapping = message.getPayloadMapping().orElse(null);
        this.sourceAddress = message.getSourceAddress().orElse(null);
        this.internalHeaders = message.getInternalHeaders();
    }

    /**
     * Constructs a new MutableExternalMessageBuilder initialized with the passed {@code headers}.
     *
     * @param headers the headers to use for initialization.
     */
    UnmodifiableExternalMessageBuilder(final Map<String, String> headers) {
        this.headers = new HashMap<>(headers);
    }

    @Override
    public ExternalMessageBuilder withAdditionalHeaders(final String key, final String value) {
        headers.put(key, value);
        return this;
    }

    @Override
    public ExternalMessageBuilder withAdditionalHeaders(final Map<String, String> additionalHeaders) {
        headers.putAll(additionalHeaders);
        return this;
    }

    @Override
    public ExternalMessageBuilder withHeaders(final Map<String, String> headers) {
        ConditionChecker.checkNotNull(headers);
        this.headers = new HashMap<>(headers);
        return this;
    }

    @Override
    public ExternalMessageBuilder withText(@Nullable final String text) {
        this.payloadType = ExternalMessage.PayloadType.TEXT;
        this.textPayload = text;
        this.bytePayload = null;
        return this;
    }

    @Override
    public ExternalMessageBuilder withBytes(@Nullable final byte[] bytes) {
        if (Objects.isNull(bytes)) {
            return withBytes((ByteBuffer) null);
        } else {
            return withBytes(ByteBuffer.wrap(bytes));
        }
    }

    @Override
    public ExternalMessageBuilder withBytes(@Nullable final ByteBuffer bytes) {
        this.payloadType = ExternalMessage.PayloadType.BYTES;
        this.bytePayload = bytes;
        this.textPayload = null;
        return this;
    }

    @Override
    public ExternalMessageBuilder withTextAndBytes(@Nullable final String text, @Nullable final byte[] bytes) {
        this.payloadType = ExternalMessage.PayloadType.TEXT_AND_BYTES;
        this.textPayload = text;
        if (Objects.isNull(bytes)) {
            this.bytePayload = null;
        } else {
            this.bytePayload = ByteBuffer.wrap(bytes);
        }
        return this;
    }

    @Override
    public ExternalMessageBuilder withTextAndBytes(@Nullable final String text, @Nullable final ByteBuffer bytes) {
        this.payloadType = ExternalMessage.PayloadType.TEXT_AND_BYTES;
        this.textPayload = text;
        this.bytePayload = bytes;
        return this;
    }

    @Override
    public ExternalMessageBuilder withAuthorizationContext(final AuthorizationContext authorizationContext) {
        this.authorizationContext = authorizationContext;
        return this;
    }

    @Override
    public ExternalMessageBuilder withTopicPath(final TopicPath topicPath) {
        this.topicPath = topicPath;
        return this;
    }

    @Override
    public <F extends EnforcementFilter<Signal<?>>> ExternalMessageBuilder withEnforcement(
            @Nullable final F enforcementFilter) {
        this.enforcementFilter = enforcementFilter;
        return this;
    }

    @Override
    public ExternalMessageBuilder withHeaderMapping(@Nullable final HeaderMapping headerMapping) {
        this.headerMapping = headerMapping;
        return this;
    }

    @Override
    public ExternalMessageBuilder withPayloadMapping(final PayloadMapping payloadMapping) {
        this.payloadMapping = payloadMapping;
        return this;
    }

    @Override
    public ExternalMessageBuilder withSourceAddress(@Nullable final String sourceAddress) {
        this.sourceAddress = sourceAddress;
        return this;
    }

    @Override
    public ExternalMessageBuilder withSource(@Nullable final Source source) {
        this.source = source;
        return this;
    }

    @Override
    public ExternalMessageBuilder asResponse(final boolean response) {
        this.response = response;
        return this;
    }

    @Override
    public ExternalMessageBuilder asError(final boolean error) {
        this.error = error;
        return this;
    }

    @Override
    public ExternalMessageBuilder withInternalHeaders(final DittoHeaders internalHeaders) {
        this.internalHeaders = internalHeaders;
        return this;
    }

    @Override
    public ExternalMessage build() {
        return new UnmodifiableExternalMessage(headers, response, error, payloadType, textPayload, bytePayload,
                authorizationContext, topicPath, enforcementFilter, headerMapping,
                payloadMapping, sourceAddress, source, internalHeaders);
    }

}
