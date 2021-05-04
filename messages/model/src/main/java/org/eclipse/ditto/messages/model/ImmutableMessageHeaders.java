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
package org.eclipse.ditto.messages.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.AbstractDittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.things.model.ThingId;

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
                .map(String::toUpperCase)
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
    public ThingId getEntityId() {
        final String thingId = getStringForDefinition(MessageHeaderDefinition.THING_ID)
                .orElseThrow(() -> newIllegalStateException(MessageHeaderDefinition.THING_ID));

        return ThingId.of(thingId);
    }

    @Override
    public Optional<String> getFeatureId() {
        return getStringForDefinition(MessageHeaderDefinition.FEATURE_ID);
    }

    @Override
    public Optional<OffsetDateTime> getTimestamp() {
        return getStringForDefinition(MessageHeaderDefinition.TIMESTAMP).map(OffsetDateTime::parse);
    }

    @Override
    public Optional<HttpStatus> getHttpStatus() {
        return getStringForDefinition(MessageHeaderDefinition.STATUS_CODE)
                .map(Integer::parseInt)
                .flatMap(HttpStatus::tryGetInstance);
    }

    @Override
    protected Optional<HeaderDefinition> getSpecificDefinitionByKey(final CharSequence key) {
        // keep the order to guarantee proper result because of timeout definitions
        Optional<HeaderDefinition> result = DittoHeaderDefinition.forKey(key);
        if (!result.isPresent()) {
            result = MessageHeaderDefinition.forKey(key);
        }
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
