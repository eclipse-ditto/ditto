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
package org.eclipse.ditto.gateway.service.streaming.actors;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.mockito.Mockito;

/**
 * Creates {@link SessionedJsonifiable}s of signals for tests outside of this package, e.g. for publishing them into
 * the stream of an SSE route as the streaming session actor would do.
 */
public final class SessionedSignals {

    private SessionedSignals() {
        throw new AssertionError();
    }

    /**
     * Creates a sessioned signal belonging to a streaming session with the given filter and extra fields.
     *
     * @param signal the signal to publish.
     * @param sessionHeaders the headers of the streaming session.
     * @param filter the RQL filter of the streaming session or {@code null}.
     * @param extraFields the extra fields of the streaming session or {@code null}.
     * @return the sessioned signal.
     */
    public static SessionedJsonifiable of(final Signal<?> signal, final DittoHeaders sessionHeaders,
            @Nullable final String filter, @Nullable final ThingFieldSelector extraFields) {

        return of(signal, sessionHeaders, filter, extraFields, ActorRef.noSender());
    }

    /**
     * Like {@link #of(Signal, DittoHeaders, String, ThingFieldSelector)}, with the streaming session actor which
     * receives e.g. the weak acknowledgements issued for the signal.
     *
     * @param signal the signal to publish.
     * @param sessionHeaders the headers of the streaming session.
     * @param filter the RQL filter of the streaming session or {@code null}.
     * @param extraFields the extra fields of the streaming session or {@code null}.
     * @param streamingSessionActor the streaming session actor.
     * @return the sessioned signal.
     */
    public static SessionedJsonifiable of(final Signal<?> signal, final DittoHeaders sessionHeaders,
            @Nullable final String filter, @Nullable final ThingFieldSelector extraFields,
            final ActorRef streamingSessionActor) {

        final StreamingSession session = StreamingSession.of(List.of(), Set.of(),
                null == filter ? null :
                        StreamingSession.getFilterCriteriaFactory().filterCriteria(filter, sessionHeaders),
                extraFields,
                streamingSessionActor,
                Mockito.mock(ThreadSafeDittoLoggingAdapter.class, Mockito.RETURNS_SELF));
        return SessionedJsonifiable.signal(signal, sessionHeaders, session);
    }

}
