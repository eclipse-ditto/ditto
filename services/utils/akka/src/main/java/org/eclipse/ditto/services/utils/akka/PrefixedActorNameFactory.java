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
package org.eclipse.ditto.services.utils.akka;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Generates prefixed actor names.
 *
 * <p>This is currently not supported by plain Akka,
 * see <a href="https://groups.google.com/forum/#!topic/akka-user/XQBwUTeSJj4">Akka User Group</a>.
 * </p>
 */
@ThreadSafe
public final class PrefixedActorNameFactory {

    private static final String PREFIX_SEPARATOR = "-";
    private final String prefix;
    private final AtomicLong counter = new AtomicLong(0);

    private PrefixedActorNameFactory(final String prefix) {
        if (prefix == null || prefix.length() == 0) {
            throw new IllegalArgumentException("prefix must not be empty");
        }
        this.prefix = prefix;
    }

    /**
     * Creates a new instance of this factory which generates actor names with the specified {@code prefix}.
     *
     * @param prefix the prefix of the generated actor names.
     *
     * @return the created instance
     */
    public static PrefixedActorNameFactory of(final String prefix) {
        return new PrefixedActorNameFactory(prefix);
    }

    /**
     * Creates a new prefixed actor name.
     *
     * @return the actor name.
     */
    public String createActorName() {
        final StringBuilder actorNameBuilder = new StringBuilder();
        actorNameBuilder.append(prefix).append(PREFIX_SEPARATOR);

        akka.util.Helpers.base64(counter.getAndIncrement(), actorNameBuilder);

        return actorNameBuilder.toString();
    }
}
