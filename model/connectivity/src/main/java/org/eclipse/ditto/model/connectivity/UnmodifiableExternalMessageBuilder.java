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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.ConditionChecker;

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
    @Nullable private ThingIdEnforcement thingIdEnforcement;

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
        this.thingIdEnforcement = message.getThingIdEnforcement().orElse(null);
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
    public ExternalMessageBuilder withAuthorizationContext(final AuthorizationContext authorizationContext) {
        this.authorizationContext = authorizationContext;
        return this;
    }

    @Override
    public ExternalMessageBuilder withThingIdEnforcement(@Nullable final ThingIdEnforcement thingIdEnforcement) {
        this.thingIdEnforcement = thingIdEnforcement;
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
    public ExternalMessage build() {
        return new UnmodifiableExternalMessage(headers, response, error, payloadType, textPayload, bytePayload,
                authorizationContext, thingIdEnforcement);
    }

}
