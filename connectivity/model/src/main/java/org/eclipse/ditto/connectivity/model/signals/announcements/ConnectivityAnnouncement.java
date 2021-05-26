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
package org.eclipse.ditto.connectivity.model.signals.announcements;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.announcements.Announcement;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

/**
 * Announcements for connections.
 *
 * @since 2.1.0
 */
public interface ConnectivityAnnouncement<T extends ConnectivityAnnouncement<T>>
        extends Announcement<T>, SignalWithEntityId<T> {

    /**
     * The service prefix for connectivity announcement commands.
     */
    String SERVICE_PREFIX = "connectivity";

    /**
     * Type prefix of connection announcements.
     */
    String TYPE_PREFIX = SERVICE_PREFIX + "." + TYPE_QUALIFIER + ":";

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
