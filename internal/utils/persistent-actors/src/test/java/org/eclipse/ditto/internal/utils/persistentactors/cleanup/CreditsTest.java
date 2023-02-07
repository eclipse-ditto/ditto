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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.client.result.DeleteResult;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link Credits}.
 */
public final class CreditsTest {

    private final ActorSystem actorSystem = ActorSystem.create();

    private Materializer materializer;
    private LongAccumulator mockTimer;

    @Before
    public void init() {
        materializer = SystemMaterializer.get(actorSystem).materializer();
        mockTimer = mock(LongAccumulator.class);
    }

    @After
    public void cleanUp() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void requestElementsUpstreamOneAtATime() {
        when(mockTimer.getThenReset()).thenReturn(0L);
        final Credits underTest = new Credits(getFastCreditConfig(4), mockTimer);
        final var probePair = materializeProbePair(underTest);
        final var sourceProbe = probePair.first();
        final var sinkProbe = probePair.second();
        sinkProbe.ensureSubscription();
        sinkProbe.request(12);
        assertThat(sourceProbe.expectRequest()).isEqualTo(1); // initial request due to input buffer
        sourceProbe.sendNext(0);
        assertThat(sourceProbe.expectRequest()).isEqualTo(1);
        sourceProbe.expectNoMessage();
    }

    @Test
    public void noElementRequestedWithoutCredit() {
        doAnswer(inv -> 1001L).when(mockTimer).getThenReset();
        final Credits underTest = new Credits(getFastCreditConfig(2), mockTimer);
        final var probePair = materializeProbePair(underTest);
        final var sourceProbe = probePair.first();
        final var sinkProbe = probePair.second();
        sinkProbe.ensureSubscription();
        sinkProbe.request(5);
        assertThat(sourceProbe.expectRequest()).isEqualTo(1L); // initial input buffer
        sourceProbe.expectNoMessage();

        doAnswer(inv -> 0L).when(mockTimer).getThenReset();
        sourceProbe.sendNext(0);
        assertThat(sourceProbe.expectRequest()).isEqualTo(1L); // credit 1/2
        doAnswer(inv -> 1001L).when(mockTimer).getThenReset();
        sourceProbe.sendNext(0);
        assertThat(sourceProbe.expectRequest()).isEqualTo(1L); // credit 2/2
        sourceProbe.sendNext(0);
        sourceProbe.expectNoMessage();
    }

    @Test
    public void onePersistenceWriteAllowedPerCredit() {
        final var mongoReadJournal = mock(MongoReadJournal.class);
        final var opsCounter = new AtomicInteger(0);

        when(mongoReadJournal.getNewestSnapshotsAbove(any(), anyInt(), eq(true), any(), any()))
                .thenReturn(Source.single(new Document().append("_id", "thing:p:id")
                        .append("__lifecycle", "DELETED")
                        .append("sn", 50L)));

        when(mongoReadJournal.getSmallestEventSeqNo(any())).thenReturn(Source.single(Optional.of(30L)));
        when(mongoReadJournal.getSmallestSnapshotSeqNo(any())).thenReturn(Source.single(Optional.of(40L)));

        doAnswer(invocation -> {
            opsCounter.incrementAndGet();
            return Source.single(DeleteResult.acknowledged(
                    invocation.<Long>getArgument(1) * 100L + invocation.<Long>getArgument(2)));
        }).when(mongoReadJournal).deleteEvents(any(), anyLong(), anyLong());
        doAnswer(invocation -> {
            opsCounter.incrementAndGet();
            return Source.single(DeleteResult.acknowledged(
                    invocation.<Long>getArgument(1) * 1000L + invocation.<Long>getArgument(2) * 10L));
        }).when(mongoReadJournal).deleteSnapshots(any(), anyLong(), anyLong());

        // mock timer permits 1 batch of credit, after which no credit is given out
        final var mockTimerResult = new AtomicLong(0L);
        doAnswer(inv -> mockTimerResult.getAndSet(1001L)).when(mockTimer).getThenReset();
        final var cleanup = new Cleanup(mongoReadJournal, materializer, () -> Pair.create(0, 1),
                Duration.ZERO, 1, 4, true);
        final var underTest = new Credits(getFastCreditConfig(4), mockTimer);

        final var log = Logging.getLogger(actorSystem, this);
        final var sinkProbe = underTest.regulate(cleanup.getCleanupStream(""), log)
                .flatMapConcat(x -> x)
                .toMat(TestSink.probe(actorSystem), Keep.right())
                .withAttributes(Attributes.inputBuffer(1, 1))
                .run(materializer);

        sinkProbe.ensureSubscription();
        sinkProbe.request(2L);
        sinkProbe.expectNextN(2L);
        sinkProbe.expectNoMessage();
        assertThat(opsCounter.get()).isEqualTo(3); // 2 requested, 1 buffered
        sinkProbe.request(10L);
        sinkProbe.expectNextN(2L);
        sinkProbe.expectNoMessage();
        assertThat(opsCounter.get()).isEqualTo(4); // 4 credit given out in total
    }

    private Pair<TestPublisher.Probe<Object>, TestSubscriber.Probe<Object>> materializeProbePair(
            final Credits credits) {
        return credits.regulate(TestSource.probe(actorSystem), Logging.getLogger(actorSystem, this))
                .toMat(TestSink.probe(actorSystem), Keep.both())
                .withAttributes(Attributes.inputBuffer(1, 1))
                .run(materializer);
    }

    private static CleanupConfig getFastCreditConfig(final int creditPerBatch) {
        return new DefaultCleanupConfig(true, Duration.ZERO, Duration.ZERO, Duration.ofMillis(100), Duration.ofNanos(1000),
                creditPerBatch, 100, 100, false);
    }
}
