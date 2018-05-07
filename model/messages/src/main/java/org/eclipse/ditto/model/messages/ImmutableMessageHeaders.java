/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.messages;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.AbstractDittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;

/**
 * Immutable implementation of {@code MessageHeaders}.
 */
@Immutable
final class ImmutableMessageHeaders extends AbstractDittoHeaders implements MessageHeaders {

    private ImmutableMessageHeaders(final Map<String, String> theDittoHeaders) {
        super(theDittoHeaders);
    }

    /**
     * Returns a new instance of {@code ImmutableMessageHeaders} which wraps the specified DittoHeaders.
     *
     * @param dittoHeaders the DittoHeaders to be wrapped.
     * @return the instance.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    static MessageHeaders of(final DittoHeaders dittoHeaders) {
        return new ImmutableMessageHeaders(checkNotNull(dittoHeaders, "Ditto Headers"));
    }

    @Override
    public MessageDirection getDirection() {
        final HeaderDefinition definition = MessageHeaderDefinition.DIRECTION;
        return getStringForDefinition(definition)
                .map(MessageDirection::valueOf)
                .orElseThrow(() -> newIllegalStateException(definition));
    }

    private static IllegalStateException newIllegalStateException(final HeaderDefinition definition) {
        final String msgTemplate = "MessageHeaders did not contain a value for key <{0}>!";
        return new IllegalStateException(MessageFormat.format(msgTemplate, definition.getKey()));
    }

    @Override
    public String getSubject() {
        final HeaderDefinition definition = MessageHeaderDefinition.SUBJECT;
        return getStringForDefinition(definition).orElseThrow(() -> newIllegalStateException(definition));
    }

    @Override
    public String getThingId() {
        final HeaderDefinition definition = MessageHeaderDefinition.THING_ID;
        return getStringForDefinition(definition).orElseThrow(() -> newIllegalStateException(definition));
    }

    @Override
    public Optional<String> getFeatureId() {
        return getStringForDefinition(MessageHeaderDefinition.FEATURE_ID);
    }

    @Override
    public Optional<Duration> getTimeout() {
        return getStringForDefinition(MessageHeaderDefinition.TIMEOUT)
                .map(Long::parseLong)
                .map(Duration::ofSeconds);
    }

    @Override
    public Optional<OffsetDateTime> getTimestamp() {
        return getStringForDefinition(MessageHeaderDefinition.TIMESTAMP).map(OffsetDateTime::parse);
    }

    @Override
    public Optional<HttpStatusCode> getStatusCode() {
        return getStringForDefinition(MessageHeaderDefinition.STATUS_CODE)
                .map(Integer::parseInt)
                .flatMap(HttpStatusCode::forInt);
    }

    @Override
    public Optional<String> getValidationUrl() {
        return getStringForDefinition(MessageHeaderDefinition.VALIDATION_URL);
    }

    @Override
    protected Optional<HeaderDefinition> getSpecificDefinitionByKey(final CharSequence key) {
        return MessageHeaderDefinition.forKey(key);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
