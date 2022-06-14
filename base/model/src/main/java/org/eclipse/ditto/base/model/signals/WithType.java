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
package org.eclipse.ditto.base.model.signals;

import javax.annotation.Nullable;

/**
 * Implementations of this interface are associated to an entity identified by the value returned from
 * {@link #getType()}.
 */
public interface WithType {

    /**
     * Commonly known type prefix for {@code MessageCommand}s.
     * @since 3.0.0
     */
    String MESSAGES_COMMANDS_PREFIX = "messages.commands:";

    /**
     * Commonly known type prefix for {@code MessageCommandResponse}s.
     * @since 3.0.0
     */
    String MESSAGES_COMMAND_RESPONSES_PREFIX = "messages.responses:";

    /**
     * Commonly known type prefix for {@code ThingCommand}s.
     * @since 3.0.0
     */
    String THINGS_COMMANDS_PREFIX = "things.commands:";

    /**
     * Commonly known type prefix for {@code ThingCommandResponses}s.
     * @since 3.0.0
     */
    String THINGS_COMMAND_RESPONSES_PREFIX = "things.responses:";

    /**
     * Commonly known type prefix for {@code ThingEvent}s.
     * @since 3.0.0
     */
    String THINGS_EVENTS_PREFIX = "things.events:";

    /**
     * Commonly known type prefix for {@code PolicyAnnouncement}s.
     * @since 3.0.0
     */
    String POLICY_ANNOUNCEMENT_PREFIX = "policies.announcements:";

    /**
     * Returns the type of this entity.
     *
     * @return the type.
     */
    String getType();

    /**
     * Checks whether the passed in {@code withType} starts with the passed in {@code typePrefix} in its
     * {@link WithType#getType()}.
     *
     * @param withType the signal to check in.
     * @param typePrefix the type prefix to check for.
     * @return {@code true} when the passed in signal's type starts with the passed in type prefix.
     * @since 3.0.0
     */
    static boolean hasTypePrefix(@Nullable final WithType withType, final String typePrefix) {
        final boolean result;
        if (null != withType) {
            final String signalType = withType.getType();
            result = signalType.startsWith(typePrefix);
        } else {
            result = false;
        }
        return result;
    }
}
