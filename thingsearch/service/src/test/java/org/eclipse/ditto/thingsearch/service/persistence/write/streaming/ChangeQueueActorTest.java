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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.junit.After;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ChangeQueueActor};
 */
public final class ChangeQueueActorTest {

    private static final ThingId THING_ID = ThingId.of("thing:id");

    private final ActorSystem system = ActorSystem.create();

    @After
    public void shutdown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aggregateThingEvents() {
        new TestKit(system) {{
            final ActorRef underTest = system.actorOf(ChangeQueueActor.props());
            final long start = 1;
            final long end = 5;
            for (long i = start; i <= end; ++i) {
                underTest.tell(metadataWithEvent(i), getRef());
            }
            underTest.tell(ChangeQueueActor.Control.DUMP, getRef());
            final Map<ThingId, Metadata> map = expectMsgClass(Map.class);
            final Metadata metadata = Objects.requireNonNull(map.get(THING_ID));
            assertThat(metadata.getThingRevision()).isEqualTo(end);
            assertThat(metadata.getEvents().stream().map(ThingEvent::getRevision).collect(Collectors.toList()))
                    .isEqualTo(LongStream.rangeClosed(start, end).boxed().collect(Collectors.toList()));
        }};
    }

    private static Metadata metadataWithEvent(final long seqNr) {
        return Metadata.of(THING_ID, seqNr, null, null,
                List.of(ThingMerged.of(THING_ID, JsonPointer.of("attributes/seqNr"), JsonValue.of(seqNr), seqNr, null,
                        DittoHeaders.empty(), null)),
                null, null);
    }
}
