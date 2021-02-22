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
package org.eclipse.ditto.signals.announcements.base;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.Signal;

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
