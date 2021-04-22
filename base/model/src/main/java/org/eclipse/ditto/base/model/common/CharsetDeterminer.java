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
package org.eclipse.ditto.base.model.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Determines the charset from a given content-type or falls back to UTF-8 if no specific one was present
 * in content-type.
 */
@Immutable
public final class CharsetDeterminer implements Function<CharSequence, Charset> {

    private static final Pattern CHARSET_PATTERN = Pattern.compile(";.?charset=");

    @Nullable private static CharsetDeterminer instance = null;

    private CharsetDeterminer() {
        super();
    }

    /**
     * Returns an instance of {@code CharsetDeterminer}.
     *
     * @return the instance.
     */
    public static CharsetDeterminer getInstance() {
        CharsetDeterminer result = instance;
        if (null == result) {
            result = new CharsetDeterminer();
            instance = result;
        }
        return result;
    }

    @Override
    public Charset apply(@Nullable final CharSequence contentType) {
        if (null != contentType) {
            final String[] withCharset = CHARSET_PATTERN.split(contentType, 2);
            if (2 == withCharset.length && Charset.isSupported(withCharset[1])) {
                return Charset.forName(withCharset[1]);
            }
        }

        return StandardCharsets.UTF_8;
    }

}
