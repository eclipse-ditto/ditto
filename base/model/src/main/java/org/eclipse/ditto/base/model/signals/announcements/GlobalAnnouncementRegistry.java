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

import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.JsonParsableAnnouncement;
import org.eclipse.ditto.base.model.signals.AbstractAnnotationBasedJsonParsableFactory;
import org.eclipse.ditto.base.model.signals.AbstractGlobalJsonParsableRegistry;

/**
 * Contains all strategies to deserialize subclasses of {@code Announcement} from a combination of
 * {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 *
 * @since 2.0.0
 */
@Immutable
public final class GlobalAnnouncementRegistry
        extends AbstractGlobalJsonParsableRegistry<Announcement<?>, JsonParsableAnnouncement> {

    private static final GlobalAnnouncementRegistry INSTANCE = new GlobalAnnouncementRegistry();

    private GlobalAnnouncementRegistry() {
        super(Announcement.class, JsonParsableAnnouncement.class, new AnnouncementParsingStrategyFactory(),
                Collections.emptyMap());
    }

    /**
     * Gets an instance of GlobalErrorRegistry.
     *
     * @return the instance.
     */
    public static GlobalAnnouncementRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValue(Announcement.JsonFields.JSON_TYPE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(Announcement.JsonFields.JSON_TYPE.getPointer().toString())
                        .build());
    }

    /**
     * Contains all strategies to deserialize a {@code Announcement} annotated with
     * {@link org.eclipse.ditto.base.model.json.JsonParsableAnnouncement}
     * from a combination of {@link org.eclipse.ditto.json.JsonObject} and
     * {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
     */
    private static final class AnnouncementParsingStrategyFactory
            extends AbstractAnnotationBasedJsonParsableFactory<Announcement<?>, JsonParsableAnnouncement> {

        private AnnouncementParsingStrategyFactory() {}

        @Override
        protected String getKeyFor(final JsonParsableAnnouncement annotation) {
            return annotation.type();
        }

        @Override
        protected String getMethodNameFor(final JsonParsableAnnouncement annotation) {
            return annotation.method();
        }
    }

}
