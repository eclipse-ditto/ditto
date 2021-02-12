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

import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.JsonParsableNotification;
import org.eclipse.ditto.signals.base.AbstractAnnotationBasedJsonParsableFactory;
import org.eclipse.ditto.signals.base.AbstractGlobalJsonParsableRegistry;

/**
 * Contains all strategies to deserialize subclasses of {@code Notification} from a combination of
 * {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.model.base.headers.DittoHeaders}.
 */
@Immutable
public final class GlobalNotificationRegistry
        extends AbstractGlobalJsonParsableRegistry<Notification<?>, JsonParsableNotification> {

    private static final GlobalNotificationRegistry INSTANCE = new GlobalNotificationRegistry();

    private GlobalNotificationRegistry() {
        super(Notification.class, JsonParsableNotification.class, new NotificationParsingStrategyFactory(),
                Collections.emptyMap());
    }

    /**
     * Gets an instance of GlobalErrorRegistry.
     *
     * @return the instance.
     */
    public static GlobalNotificationRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValue(Notification.JSON_TYPE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(Notification.JSON_TYPE.getPointer().toString())
                        .build());
    }

    /**
     * Contains all strategies to deserialize a {@code Notification} annotated with
     * {@link JsonParsableNotification}
     * from a combination of {@link org.eclipse.ditto.json.JsonObject} and
     * {@link org.eclipse.ditto.model.base.headers.DittoHeaders}.
     */
    private static final class NotificationParsingStrategyFactory
            extends AbstractAnnotationBasedJsonParsableFactory<Notification<?>, JsonParsableNotification> {

        private NotificationParsingStrategyFactory() {}

        @Override
        protected String getV1FallbackKeyFor(final JsonParsableNotification annotation) {
            return getKeyFor(annotation);
        }

        @Override
        protected String getKeyFor(final JsonParsableNotification annotation) {
            return annotation.type();
        }

        @Override
        protected String getMethodNameFor(final JsonParsableNotification annotation) {
            return annotation.method();
        }
    }

}
