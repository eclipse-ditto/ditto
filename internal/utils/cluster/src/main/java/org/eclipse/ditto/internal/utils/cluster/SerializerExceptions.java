/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster;

import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * This class is a mere namespace for exceptions that {@link akka.serialization.Serializer}s might throw.
 */
@Immutable
public final class SerializerExceptions {

    private SerializerExceptions() {
        throw new AssertionError();
    }

    /**
     * Thrown to indicate that a Serializer failed to serialize a particular object.
     */
    public static final class SerializationFailed extends RuntimeException {

        private static final long serialVersionUID = -98905394779998394L;

        public SerializationFailed(final String message, @Nullable final Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * Thrown to indicate that a Serializer does not support serialization of a particular object type.
     */
    public static final class NotSerializable extends RuntimeException {

        private static final long serialVersionUID = 189470272351263960L;

        public NotSerializable(final CharSequence serializerName, final Object notSerializableObject) {
            super(getMessage(serializerName, notSerializableObject));
        }

        private static String getMessage(final CharSequence serializerName, final Object notSerializableObject) {
            final var pattern = "Serializer <{0}> can''t serialize object of type <{1}>.";
            return MessageFormat.format(pattern, serializerName, notSerializableObject.getClass());
        }

    }

    /**
     * Thrown to indicate that a Serializer is unable to handle a particular manifest.
     */
    public static final class UnsupportedManifest extends RuntimeException {

        private static final long serialVersionUID = 1535796318132189519L;

        public UnsupportedManifest(final String manifest) {
            super(MessageFormat.format("Serializer does not support manifest <{0}>.", manifest));
        }

    }

}
