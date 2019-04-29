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
package org.eclipse.ditto.services.thingsearch.persistence.write;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;


/**
 * Class that helps to enforce size restrictions on the things model.
 */
public final class IndexLengthRestrictionEnforcer {

    private static final int DEFAULT_VALUE_LENGTH = 4;

    /**
     * Max allowed length of index content.
     */
    static final int MAX_INDEX_CONTENT_LENGTH = 950;

    /**
     * Reserved length for auth subjects.
     */
    static final int AUTHORIZATION_SUBJECT_OVERHEAD = 128;

    /**
     * The logging adapter used to log size restriction enforcements.
     */
    private final int thingIdNamespaceOverhead;

    private IndexLengthRestrictionEnforcer(final String thingId) {
        this.thingIdNamespaceOverhead = calculateThingIdNamespaceAuthSubjectOverhead(thingId);
    }

    /**
     * Create a new instance of {@link IndexLengthRestrictionEnforcer}.
     *
     * @param thingId ID of the thing.
     * @return the instance.
     */
    public static IndexLengthRestrictionEnforcer newInstance(final String thingId) {
        checkThingId(thingId);
        return new IndexLengthRestrictionEnforcer(thingId);
    }

    /**
     * Enforce index length restriction.
     *
     * @param pointer pointer to a Json value.
     * @param value the JSON value.
     * @return the result.
     */
    public Optional<JsonValue> enforce(final JsonPointer pointer, final JsonValue value) {
        if (violatesThreshold(pointer, value, thingIdNamespaceOverhead)) {
            return fixViolation(pointer, value, thingIdNamespaceOverhead);
        } else {
            return Optional.of(value);
        }
    }

    private static Optional<JsonValue> fixViolation(final JsonPointer key, final JsonValue value, final int overhead) {
        if (value.isString()) {
            final int cutAt = Math.max(0, MAX_INDEX_CONTENT_LENGTH - totalOverhead(key, overhead));
            final JsonValue fixedValue = JsonValue.of(value.asString().substring(0, cutAt));
            if (!violatesThreshold(key, fixedValue, overhead)) {
                return Optional.of(fixedValue);
            }
        }
        return Optional.empty();
    }

    private static boolean violatesThreshold(final JsonPointer key, final JsonValue value, final int overhead) {
        final int valueLength = null != value && value.isString() && !value.isNull()
                ? value.asString().length()
                : DEFAULT_VALUE_LENGTH;
        return MAX_INDEX_CONTENT_LENGTH < valueLength + totalOverhead(key, overhead);
    }

    private static int totalOverhead(final JsonPointer key, final int additionalOverhead) {
        return jsonPointerLength(key) + additionalOverhead;
    }

    private static int calculateThingIdNamespaceAuthSubjectOverhead(final String thingId) {
        final int namespaceLength = Math.max(0, thingId.indexOf(':'));
        final int authSubjectOverhead = 128;
        return thingId.length() + namespaceLength + authSubjectOverhead;
    }

    private static int jsonPointerLength(final JsonPointer jsonPointer) {
        return jsonPointer.toString().length();
    }

    private static void checkThingId(final String thingId) {
        requireNonNull(thingId);
        if (thingId.isEmpty()) {
            throw new IllegalArgumentException("Thing ID must not be empty!");
        }

    }
}
