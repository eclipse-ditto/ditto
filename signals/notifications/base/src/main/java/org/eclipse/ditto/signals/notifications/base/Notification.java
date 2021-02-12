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
package org.eclipse.ditto.signals.notifications.base;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Notifications are subscribable signals that are not persisted.
 *
 * @param <T> the concrete type of notification.
 */
@IndexSubclasses
public interface Notification<T extends Notification<T>> extends Signal<T> {

    /**
     * Type qualifier of notifications.
     */
    String TYPE_QUALIFIER = "notification";

    /**
     * Json field containing the type of this signal.
     */
    JsonFieldDefinition<String> JSON_TYPE =
            JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_2);
}
