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
package org.eclipse.ditto.base.model.signals.announcements;

import javax.annotation.Nullable;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Announcements are subscribable signals that are not persisted.
 *
 * @param <T> the concrete type of announcement.
 * @since 2.0.0
 */
@IndexSubclasses
public interface Announcement<T extends Announcement<T>> extends Signal<T> {

    /**
     * Type qualifier of announcements.
     */
    String TYPE_QUALIFIER = "announcements";

    /**
     * Retrieve the name of the announcement. Used as a part of the topic path in Ditto protocol.
     *
     * @return name of the announcement.
     */
    @Override
    String getName();

    /**
     * Indicates whether the specified signal argument is a {@code PolicyAnnouncement} without requiring a direct
     * dependency to the policies-model.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code PolicyAnnouncement}, {@code false} else.
     * @since 3.0.0
     */
    static boolean isPolicyAnnouncement(@Nullable final WithType signal) {
        return WithType.hasTypePrefix(signal, WithType.POLICY_ANNOUNCEMENT_PREFIX);
    }

    /**
     * Definition of fields of the JSON representation of an {@link Announcement}.
     */
    final class JsonFields {

        /**
         * Json field containing the type of this signal.
         */
        public static final JsonFieldDefinition<String> JSON_TYPE =
                JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
