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
package org.eclipse.ditto.thingsearch.service.persistence.write;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
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
        final int keyOverhead = jsonPointerBytes(pointer) + thingIdNamespaceOverhead;
        if (keyOverhead > MAX_INDEX_CONTENT_LENGTH - DEFAULT_VALUE_LENGTH) {
            // not possible to trim key-value pair; do not index this entry.
            return Optional.empty();
        } else if (value.isString()) {
            return Optional.of(fixViolation(value, keyOverhead));
        } else if (isNonEmptyComposite(value)) {
            // unexpected composite value - complain
            throw new IllegalArgumentException("value should not be an array or object but it is");
        } else {
            return Optional.of(value);
        }
    }

    private static boolean isNonEmptyComposite(final JsonValue value) {
        return value.isObject() && !value.asObject().isEmpty() ||
                value.isArray() && !value.asArray().isEmpty();
    }

    // precondition: value.isString()
    private static JsonValue fixViolation(final JsonValue value, final int keyOverhead) {
        final String valueString = value.asString();
        final int bytesForValueString = MAX_INDEX_CONTENT_LENGTH - keyOverhead;
        // encode valueString into a byte buffer of size == bytesForValueString
        final CharBuffer charBuffer = CharBuffer.wrap(valueString.toCharArray());
        final ByteBuffer byteBuffer = ByteBuffer.allocate(bytesForValueString);
        final CoderResult coderResult = UTF_8.newEncoder().encode(charBuffer, byteBuffer, true);
        if (coderResult == CoderResult.OVERFLOW) {
            // the buffer overflew; truncate the value string by re-encoding the buffer content
            return JsonValue.of(UTF_8.decode(byteBuffer.flip()));
        } else {
            // the buffer did not overflow; the original value satisfies the length restriction
            return value;
        }
    }

    private static int calculateThingIdNamespaceAuthSubjectOverhead(final String thingId) {
        final int namespaceLength = Math.max(0, thingId.indexOf(':'));
        final int authSubjectOverhead = 128;
        return thingId.length() + namespaceLength + authSubjectOverhead;
    }

    private static int jsonPointerBytes(final JsonPointer jsonPointer) {
        return jsonPointer.toString().getBytes(UTF_8).length;
    }

    private static void checkThingId(final String thingId) {
        requireNonNull(thingId);
        if (thingId.isEmpty()) {
            throw new IllegalArgumentException("Thing ID must not be empty!");
        }

    }
}
