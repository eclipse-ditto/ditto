/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.junit.Test;
import org.komamitsu.fluency.Fluency;

/**
 * Unit test for {@link FluentPublishingConnectionLogger}.
 */
public final class FluentPublishingConnectionLoggerTest {

    @Test
    public void closeDoesNotCloseSharedFluencyForwarder() throws IOException {
        final Fluency fluency = mock(Fluency.class);
        final FluentPublishingConnectionLogger underTest = buildLogger(fluency);

        underTest.close();

        verifyNoInteractions(fluency);
    }

    @Test
    public void loggerStillEmitsAfterCloseIsCalledOnSibling() throws IOException {
        final Fluency fluency = mock(Fluency.class);

        // Simulate two loggers sharing the same Fluency (as in production)
        final FluentPublishingConnectionLogger loggerA = buildLogger(fluency);
        final FluentPublishingConnectionLogger loggerB = buildLogger(fluency);

        // Close logger A (e.g., connection invalidated)
        loggerA.close();

        // Logger B should still be able to emit
        final ConnectionMonitor.InfoProvider info = mock(ConnectionMonitor.InfoProvider.class);
        when(info.getCorrelationId()).thenReturn("test-correlation");
        when(info.getTimestamp()).thenReturn(Instant.now());

        loggerB.success(info, "test message after sibling close");

        verify(fluency).emit(anyString(), any(), anyMap());
    }

    private static FluentPublishingConnectionLogger buildLogger(final Fluency fluency) {
        return FluentPublishingConnectionLogger
                .newBuilder(ConnectionId.of("test-connection"), LogCategory.CONNECTION, LogType.OTHER,
                        fluency, Duration.ofSeconds(5))
                .withLogLevels(List.of(LogLevel.SUCCESS, LogLevel.FAILURE))
                .build();
    }
}
