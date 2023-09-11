/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.logging;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.JsonWritingUtils;

/**
 * Logstash logback provider for providing a field {@code intLevel} for each log entry with the values, depending on
 * the log level, being:
 * <ul>
 * <li>TRACE: 1</li>
 * <li>DEBUG: 2</li>
 * <li>INFO: 3</li>
 * <li>WARN: 4</li>
 * <li>ERROR: 5</li>
 * </ul>
 * To be used in {@code logback.xml} as:
 * <pre>{@code
 * <encoder class="net.logstash.logback.encoder.LogstashEncoder">
 *    <provider class="org.eclipse.ditto.base.service.logging.IntLevelJsonProvider"/>
 * </encoder>
 * }
 * </pre>
 *
 * @since 3.4.0
 */
public final class IntLevelJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    @Override
    public void writeTo(final JsonGenerator generator, final ILoggingEvent event) throws IOException {
        JsonWritingUtils.writeNumberField(
                generator,
                "intLevel",
                mapFromLevelToIntLevel(event.getLevel())
        );
    }

    private int mapFromLevelToIntLevel(final Level level) {
        if (level.equals(Level.OFF)) {
            return 0;
        } else if (level.equals(Level.TRACE)) {
            return 1;
        } else if (level.equals(Level.DEBUG)) {
            return 2;
        } else if (level.equals(Level.INFO)) {
            return 3;
        } else if (level.equals(Level.WARN)) {
            return 4;
        } else if (level.equals(Level.ERROR)) {
            return 5;
        } else if (level.equals(Level.ALL)) {
            // should not be able to happen for a single log entry:
            return Integer.MAX_VALUE;
        } else {
            // should not be able to happen at all:
            return Integer.MIN_VALUE;
        }
    }
}
