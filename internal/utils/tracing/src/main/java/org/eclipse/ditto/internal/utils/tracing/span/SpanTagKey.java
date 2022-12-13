/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.span;

import java.net.URI;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;

/**
 * This class enumerates keys of well-known Span tags which allow to create an
 * actual {@link Tag} for an appropriate tag value.
 */
public abstract class SpanTagKey<T> {

    /**
     * Prefix to be used for well-known span tag keys.
     */
    public static final String KEY_PREFIX = "ditto.";

    public static final SpanTagKey<CharSequence> CORRELATION_ID =
            new CharSequenceImplementation(KEY_PREFIX + "correlationId");

    public static final SpanTagKey<CharSequence> SIGNAL_TYPE = new CharSequenceImplementation(KEY_PREFIX + "signal.type");

    public static final SpanTagKey<CharSequence> CHANNEL = new CharSequenceImplementation(KEY_PREFIX + "channel");

    public static final SpanTagKey<CharSequence> ENTITY_ID = new CharSequenceImplementation(KEY_PREFIX + "entityId");

    public static final SpanTagKey<CharSequence> CONNECTION_ID =
            new CharSequenceImplementation(KEY_PREFIX + "connection.id");

    public static final SpanTagKey<CharSequence> CONNECTION_TYPE =
            new CharSequenceImplementation(KEY_PREFIX + "connection.type");

    public static final SpanTagKey<HttpStatus> HTTP_STATUS = new HttpStatusImplementation(KEY_PREFIX + "statusCode");

    public static final SpanTagKey<CharSequence> REQUEST_METHOD_NAME =
            new CharSequenceImplementation(KEY_PREFIX + "request.method");

    public static final SpanTagKey<URI> REQUEST_URI = new URIImplementation(KEY_PREFIX + "request.path");

    public static final SpanTagKey<Boolean> AUTH_SUCCESS = new BooleanImplementation(KEY_PREFIX + "auth.success");

    public static final SpanTagKey<Boolean> AUTH_ERROR = new BooleanImplementation(KEY_PREFIX + "auth.error");

    private final String key;

    private SpanTagKey(final String key) {
        this.key = ConditionChecker.checkNotNull(key, "key");
    }

    private String getKey() {
        return key;
    }

    public abstract Tag getTagForValue(T value);

    @Override
    public String toString() {
        return getKey();
    }

    private static final class CharSequenceImplementation extends SpanTagKey<CharSequence> {

        private CharSequenceImplementation(final String key) {
            super(key);
        }

        @Override
        public Tag getTagForValue(final CharSequence value) {
            ConditionChecker.checkNotNull(value, "value");
            return Tag.of(toString(), value.toString());
        }

    }

    private static final class HttpStatusImplementation extends SpanTagKey<HttpStatus> {

        private HttpStatusImplementation(final String key) {
            super(key);
        }

        @Override
        public Tag getTagForValue(final HttpStatus httpStatus) {
            ConditionChecker.checkNotNull(httpStatus, "httpStatus");
            return Tag.of(toString(), httpStatus.getCode());
        }

    }

    private static final class BooleanImplementation extends SpanTagKey<Boolean> {

        private BooleanImplementation(final String key) {
            super(key);
        }

        @Override
        public Tag getTagForValue(final Boolean value) {
            ConditionChecker.checkNotNull(value, "value");
            return Tag.of(toString(), value);
        }

    }

    private static final class URIImplementation extends SpanTagKey<URI> {

        private URIImplementation(final String key) {
            super(key);
        }

        @Override
        public Tag getTagForValue(final URI uri) {
            ConditionChecker.checkNotNull(uri, "uri");
            return Tag.of(toString(), uri.toString());
        }

    }

}
