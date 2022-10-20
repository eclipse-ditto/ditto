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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class StartedKamonTimerTest {

    private StartedTimer underTest;

    @Before
    public void setup() {
        underTest = Timers.newTimer("TestTimer").start();
    }

    @Test
    public void getName() {
        assertThat(underTest.getName()).isEqualTo("TestTimer");
    }

    @Test
    public void getOnStopHandlers() {
        final OnStopHandler onStopHandler = mock(OnStopHandler.class);
        underTest.onStop(onStopHandler);
        final List<OnStopHandler> onStopHandlers = underTest.getOnStopHandlers();
        Assertions.assertThat(onStopHandlers)
                .hasSize(2)
                .contains(onStopHandler);
    }

    @Test
    public void onStopIsCalled() {
        final OnStopHandler onStopHandler = mock(OnStopHandler.class);
        underTest.onStop(onStopHandler);
        final StoppedTimer stopped = underTest.stop();
        verify(onStopHandler).handleStoppedTimer(stopped);
    }

    @Test
    public void isRunning() {
        assertThat(underTest.isRunning()).isTrue();
        underTest.stop();
        assertThat(underTest.isRunning()).isFalse();
    }

    @Test
    public void stopAStoppedTimerCausesNoException() {
        underTest.stop();
        assertThatCode(() -> underTest.stop()).doesNotThrowAnyException();
    }

    @Test
    public void getSegments() {
        assertThat(underTest.getSegments().keySet()).isEmpty();
        final StartedTimer testSegment = underTest.startNewSegment("TEST");
        final Map<String, StartedTimer> segments = underTest.getSegments();
        Assertions.assertThat(segments).hasSize(1);
        assertThat(segments).containsEntry("TEST", testSegment);
    }

    @Test
    public void segmentHasSameTags() {
        final var segmentName = "TEST";
        final var tag1 = Tag.of("tag1", "value1");
        underTest.tag(tag1);

        final var testSegment = underTest.startNewSegment(segmentName);

        assertThat(underTest.getTagSet()).containsOnly(tag1, Tag.of("segment", "overall"));
        assertThat(testSegment.getTagSet()).containsOnly(tag1, Tag.of("segment", segmentName));
    }

    @Test
    public void segmentsAreGettingStoppedToo() {
        final StartedTimer testSegment = underTest.startNewSegment("TEST");
        assertThat(testSegment.isRunning()).isTrue();
        underTest.stop();
        assertThat(testSegment.isRunning()).isFalse();
    }

    @Test
    public void onStopHandlersOfSegmentsAreCalledToo() {
        final OnStopHandler onStopHandler = mock(OnStopHandler.class);
        final OnStopHandler segmentOnStopHandler = mock(OnStopHandler.class);
        underTest.onStop(onStopHandler);
        final StartedTimer testSegment = underTest.startNewSegment("TEST");
        testSegment.onStop(segmentOnStopHandler);
        final StoppedTimer stop = underTest.stop();
        verify(onStopHandler).handleStoppedTimer(stop);
        verify(segmentOnStopHandler).handleStoppedTimer(any(StoppedTimer.class));
    }

    @Test
    public void startTimeStampAlwaysSet() {
        assertThat(underTest.getStartInstant()).satisfies(startInstant -> assertThat(startInstant.toNanos()).isPositive());
    }

    @Test
    public void canHandleAlreadyStoppedSegments() {
        final StartedTimer testSegment = underTest.startNewSegment("Test");
        testSegment.stop();
        underTest.stop();
        // No Exception occurred if successful.
    }

    @Test
    public void taggingWorks() {
        final var stringTag = Tag.of("stringTag", "1");
        final var booleanTag = Tag.of("booleanTag", false);
        underTest.tag(stringTag);
        underTest.tag(booleanTag);

        assertThat(underTest.getTagSet()).containsOnly(stringTag, booleanTag, Tag.of("segment", "overall"));
    }

}
