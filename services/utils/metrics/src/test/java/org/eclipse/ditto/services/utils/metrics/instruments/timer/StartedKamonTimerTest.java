/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.metrics.instruments.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StartedKamonTimerTest {

    private StartedTimer sut;

    @Before
    public void setup() {
        sut = PreparedKamonTimer.newTimer("TestTimer").start();
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
        assertThat(onStopHandlers).hasSize(1);
        assertThat(onStopHandlers).contains(onStopHandler);
    }

    @Test
    public void onStopIsCalled() {
        final OnStopHandler onStopHandler = mock(OnStopHandler.class);
        sut.onStop(onStopHandler);
        final StoppedTimer stop = sut.stop();
        verify(onStopHandler).handleStoppedTimer(stop);
    }

    @Test
    public void isRunning() {
        assertThat(sut.isRunning()).isTrue();
        sut.stop();
        assertThat(sut.isRunning()).isFalse();
    }

    @Test(expected = IllegalStateException.class)
    public void stopAStoppedTimerCausesException() {
        sut.stop();
        sut.stop();
    }

    @Test
    public void getSegments() {
        assertThat(sut.getSegments().keySet()).hasSize(0);
        final StartedTimer testSegment = sut.startNewSegment("TEST");
        final Map<String, StartedTimer> segments = sut.getSegments();
        assertThat(segments).hasSize(1);
        assertThat(segments.get("TEST")).isEqualTo(testSegment);
    }

    @Test
    public void segmentHasSameTags() {
        sut.tag("tag1", "value1");
        final StartedTimer testSegment = sut.startNewSegment("TEST");
        final Map<String, String> segmentTags = testSegment.getTags();
        final Map<String, String> parentTags = sut.getTags();

        assertThat(parentTags.keySet()).hasSize(2);
        assertThat(parentTags.get("segment")).isEqualTo("overall");
        assertThat(parentTags.get("tag1")).isEqualTo("value1");

        assertThat(segmentTags.keySet()).hasSize(2);
        assertThat(segmentTags.get("segment")).isEqualTo("TEST");
        assertThat(segmentTags.get("tag1")).isEqualTo("value1");
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
        assertThat(sut.getStartTimeStamp()).isGreaterThan(0);
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
