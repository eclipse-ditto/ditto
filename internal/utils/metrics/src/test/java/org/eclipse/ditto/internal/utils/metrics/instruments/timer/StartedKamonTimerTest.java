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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StartedKamonTimerTest {

    private StartedTimer sut;

    @Before
    public void setup() {
        sut = Timers.newTimer("TestTimer").start();
    }

    @Test
    public void getName() {
        assertThat(sut.getName()).isEqualTo("TestTimer");
    }

    @Test
    public void getOnStopHandlers() {
        final OnStopHandler onStopHandler = mock(OnStopHandler.class);
        sut.onStop(onStopHandler);
        final List<OnStopHandler> onStopHandlers = sut.getOnStopHandlers();
        Assertions.assertThat(onStopHandlers)
                .hasSize(2)
                .contains(onStopHandler);
    }

    @Test
    public void onStopIsCalled() {
        final OnStopHandler onStopHandler = mock(OnStopHandler.class);
        sut.onStop(onStopHandler);
        final StoppedTimer stopped = sut.stop();
        verify(onStopHandler).handleStoppedTimer(stopped);
    }

    @Test
    public void isRunning() {
        assertThat(sut.isRunning()).isTrue();
        sut.stop();
        assertThat(sut.isRunning()).isFalse();
    }

    @Test
    public void stopAStoppedTimerCausesNoException() {
        sut.stop();
        assertThatCode(() -> sut.stop()).doesNotThrowAnyException();
    }

    @Test
    public void getSegments() {
        assertThat(sut.getSegments().keySet()).isEmpty();
        final StartedTimer testSegment = sut.startNewSegment("TEST");
        final Map<String, StartedTimer> segments = sut.getSegments();
        Assertions.assertThat(segments).hasSize(1);
        assertThat(segments).containsEntry("TEST", testSegment);
    }

    @Test
    public void segmentHasSameTags() {
        sut.tag("tag1", "value1");
        final StartedTimer testSegment = sut.startNewSegment("TEST");
        final Map<String, String> segmentTags = testSegment.getTags();
        final Map<String, String> parentTags = sut.getTags();

        assertThat(parentTags.keySet()).hasSize(2);
        assertThat(parentTags).containsEntry("segment", "overall");
        assertThat(parentTags).containsEntry("tag1", "value1");

        assertThat(segmentTags.keySet()).hasSize(2);
        assertThat(segmentTags).containsEntry("segment", "TEST");
        assertThat(segmentTags).containsEntry("tag1", "value1");
    }

    @Test
    public void segmentsAreGettingStoppedToo() {
        final StartedTimer testSegment = sut.startNewSegment("TEST");
        assertThat(testSegment.isRunning()).isTrue();
        sut.stop();
        assertThat(testSegment.isRunning()).isFalse();
    }

    @Test
    public void onStopHandlersOfSegmentsAreCalledToo() {
        final OnStopHandler onStopHandler = mock(OnStopHandler.class);
        final OnStopHandler segmentOnStopHandler = mock(OnStopHandler.class);
        sut.onStop(onStopHandler);
        final StartedTimer testSegment = sut.startNewSegment("TEST");
        testSegment.onStop(segmentOnStopHandler);
        final StoppedTimer stop = sut.stop();
        verify(onStopHandler).handleStoppedTimer(stop);
        verify(segmentOnStopHandler).handleStoppedTimer(any(StoppedTimer.class));
    }

    @Test
    public void startTimeStampAlwaysSet() {
        assertThat(sut.getStartNanoTime()).isPositive();
    }

    @Test
    public void canHandleAlreadyStoppedSegments() {
        final StartedTimer testSegment = sut.startNewSegment("Test");
        testSegment.stop();
        sut.stop();
        // No Exception occurred if successful.
    }

    @Test
    public void taggingWorks() {
        sut.tag("stringTag", "1");
        sut.tag("longTag", 1L);
        sut.tag("booleanTag", false);
        sut.tag("doubleTag", 1.0);

        assertThat(sut.getTags()).hasSize(5); // 4 tags + segment tag
        assertThat(sut.getTag("stringTag")).isEqualTo("1");
        assertThat(sut.getTag("longTag")).isEqualTo("1");
        assertThat(sut.getTag("booleanTag")).isEqualTo("false");
        assertThat(sut.getTag("doubleTag")).isEqualTo("1.0");
    }
}
