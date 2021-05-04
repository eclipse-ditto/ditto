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
package org.eclipse.ditto.signals.announcements.connectivity;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityConstants;
import org.eclipse.ditto.signals.announcements.base.Announcement;
import org.eclipse.ditto.signals.base.SignalWithEntityId;

/**
 * Announcements for connections.
 *
 * @since 2.1.0
 */
public interface ConnectivityAnnouncement<T extends ConnectivityAnnouncement<T>>
        extends Announcement<T>, SignalWithEntityId<T> {

    /**
     * Type prefix of connection announcements.
     */
    String TYPE_PREFIX = "connectivity." + TYPE_QUALIFIER + ":";

    /**
     * Connection resource type.
     */
    String RESOURCE_TYPE = ConnectivityConstants.ENTITY_TYPE.toString();

    @Override
    ConnectionId getEntityId();

    /**
     * Definition of fields of the JSON representation of a {@link ConnectivityAnnouncement}.
     */
    final class JsonFields {

        /**
         * Json field for the connection ID.
         */
        public static final JsonFieldDefinition<String> JSON_CONNECTION_ID =
                JsonFactory.newStringFieldDefinition("connectionId", JsonSchemaVersion.V_2, FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
