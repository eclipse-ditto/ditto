/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.config;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.events.base.Event;

/**
 * Interface of an event that transports changes to {@link ConnectivityConfig}. It extends the
 * {@link ConnectivityConfigBuildable} interface, so with this event and a given {@link ConnectivityConfig} a
 * modified {@link ConnectivityConfig} can be built.
 *
 * @param <T> type of the implementing class
 */
public interface ConnectivityConfigEvent<T extends ConnectivityConfigEvent<T>>
        extends Event<T>, ConnectivityConfigBuildable {

    /**
     * Type Prefix of connectivity config.
     */
    String TYPE_PREFIX = "connectivity.config." + TYPE_QUALIFIER + ":";

    /**
     * Connectivity config resource type.
     */
    String RESOURCE_TYPE = "connectivity.config";

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    default String getManifest() {
        return getType();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.of("/");
    }

    /**
     * Returns {@code 0L} since this event doesn't have a revision.
     *
     * @return {@code 0L}.
     */
    @Override
    default long getRevision() {
        return 0L;
    }

    /**
     * Returns the unmodified instance of this event.
     *
     * @param revision the revision to set is ignored.
     * @return the unmodified instance.
     */
    @Override
    default T setRevision(long revision) {
        return (T) this;
    }

    /**
     * Returns all non hidden marked fields of this event.
     *
     * @return a JSON object representation of this event including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }
}
