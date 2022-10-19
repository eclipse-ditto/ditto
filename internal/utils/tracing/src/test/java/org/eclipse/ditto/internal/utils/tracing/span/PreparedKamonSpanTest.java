/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.Map;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartInstant;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.Timers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import kamon.Kamon;

/**
 * Unit test for {@link PreparedKamonSpan}.
 */
public final class PreparedKamonSpanTest {

    @Rule
    public final KamonTracingInitResource kamonTracingInitResource = KamonTracingInitResource.newInstance(
            KamonTracingInitResource.KamonTracingConfig.defaultValues()
                    .withIdentifierSchemeDouble()
                    .withSamplerAlways()
                    .withTickInterval(Duration.ofMillis(100L))
    );

    @Rule
    public final TestName testName = new TestName();

    private PreparedKamonSpan underTest;

    @Before
    public void setup() {
        underTest = PreparedKamonSpan.newInstance(
                Map.of(),
                SpanOperationName.of(testName.getMethodName()),
                KamonHttpContextPropagation.newInstanceForChannelName("default")
        );
    }

    @Test
    public void setSingleTagWorks() {
        final var stringTagKey = "stringTag";
        final var stringTagValue = "2";
        final var longTagKey = "longTag";
        final var longTagValue = Long.valueOf(2L);
        final var booleanTagKey = "booleanTag";
        final var booleanTagValue = Boolean.TRUE;
        final var doubleTagKey = "doubleTag";
        final var doubleTagValue = Double.valueOf(2.0);

        underTest.tag(stringTagKey, stringTagValue);
        underTest.tag(longTagKey, longTagValue);
        underTest.tag(booleanTagKey, booleanTagValue);
        underTest.tag(doubleTagKey, doubleTagValue);

        assertThat(underTest.getTags())
                .containsOnly(
                        Map.entry(stringTagKey, stringTagValue),
                        Map.entry(longTagKey, longTagValue.toString()),
                        Map.entry(booleanTagKey, booleanTagValue.toString()),
                        Map.entry(doubleTagKey, doubleTagValue.toString())
                );
    }

    @Test
    public void getTagWithNullKeyThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.getTag(null))
                .withMessage("The key must not be null!")
                .withNoCause();
    }

    @Test
    public void getTagForKeyWithNoAssociatedValueReturnsEmptyOptional() {
        assertThat(underTest.getTag("foo")).isEmpty();
    }

    @Test
    public void getTagForKeyWithAssociatedValueReturnsOptionalContainingThatValue() {
        final var key = "foo";
        final var value = "bar";
        underTest.tag(key, value);

        assertThat(underTest.getTag(key)).hasValue(value);
    }

    @Test
    public void getTagsReturnsExpected() {
        final var correlationId = "12345";
        final var connectionId = "connection-1";
        final var entityId = "my-entity";

        underTest.correlationId(correlationId);
        underTest.connectionId(connectionId);
        underTest.entityId(entityId);

        assertThat(underTest.getTags())
                .containsOnly(
                        Map.entry(SpanTags.CORRELATION_ID, correlationId),
                        Map.entry(SpanTags.CONNECTION_ID, connectionId),
                        Map.entry(SpanTags.ENTITY_ID, entityId)
                );
    }

    @Test
    public void nullSpanTagsAreIgnored() {
        underTest.correlationId(null);
        underTest.connectionId(null);
        underTest.entityId(null);

        assertThat(underTest.getTags()).isEmpty();
    }

    @Test
    public void tagsWithNullMapThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.tags(null))
                .withMessage("The tags must not be null!")
                .withNoCause();
    }

    @Test
    public void tagsPutsKeysAndValuesOfSpecifiedMapToTagsOfPreparedKamonSpan() {
        final var stringTagKey = "stringTag";
        final var longTagKey = "longTag";
        final var booleanTagKey = "booleanTag";
        final var doubleTagKey = "doubleTag";
        final var stringTagKeyEntry = Map.entry(stringTagKey, "Tardis");
        final var longTagKeyEntry = Map.entry(longTagKey, "23");
        final var booleanTagKeyEntry = Map.entry(booleanTagKey, "true");
        final var doubleTagKeyEntry = Map.entry(doubleTagKey, "4.2");
        final var fooEntry = Map.entry("foo", "bar");
        underTest.tag(stringTagKey, "stringTag");
        underTest.tag(longTagKey, 1L);
        underTest.tag(booleanTagKey, false);
        underTest.tag(doubleTagKey, 1.2);
        underTest.tag(fooEntry.getKey(), fooEntry.getValue());

        underTest.tags(Map.ofEntries(stringTagKeyEntry, longTagKeyEntry, booleanTagKeyEntry, doubleTagKeyEntry));

        assertThat(underTest.getTags())
                .containsOnly(
                        stringTagKeyEntry,
                        longTagKeyEntry,
                        booleanTagKeyEntry,
                        doubleTagKeyEntry,
                        fooEntry
                );
    }

    @Test
    public void selfReturnsSelf() {
        assertThat(underTest.self()).isSameAs(underTest);
    }

    @Test
    public void startReturnsStartedSpanWithExpectedTags() {
        underTest.tags(Map.of(
                "foo", "bar",
                "ping", "pong",
                "marco", "polo"
        ));
        final var spanReporter = TestSpanReporter.newInstance();
        Kamon.addReporter("mySpanReporter", spanReporter);

        final var startedSpan = underTest.start();

        final var tagsForSpanFuture = spanReporter.getTagsForSpanWithId(startedSpan.getSpanId());
        startedSpan.finish();

        assertThat(tagsForSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .isEqualTo(underTest.getTags());
    }

    @SuppressWarnings("java:S2925")
    @Test
    public void startAtReturnsStartedSpanWithExpectedTagsAndStartInstant() throws InterruptedException {
        final var startInstant = StartInstant.now();
        underTest.tags(Map.of(
                "foo", "bar",
                "ping", "pong",
                "marco", "polo"
        ));
        final var spanReporter = TestSpanReporter.newInstance();
        Kamon.addReporter("mySpanReporter", spanReporter);
        Thread.sleep(150L);

        final var startedSpan = underTest.startAt(startInstant);

        final var tagsForSpanFuture = spanReporter.getTagsForSpanWithId(startedSpan.getSpanId());
        final var startInstantForSpanFuture = spanReporter.getStartInstantForSpanWithId(startedSpan.getSpanId());
        startedSpan.finish();

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(tagsForSpanFuture)
                    .as("tags")
                    .succeedsWithin(Duration.ofSeconds(2L))
                    .isEqualTo(underTest.getTags());
            softly.assertThat(startInstantForSpanFuture)
                    .as("start instant")
                    .succeedsWithin(Duration.ofSeconds(2L))
                    .isEqualTo(startInstant.toInstant());
        }
    }

    @Test
    public void startByStartedTimerReturnsStartedSpanWithTagsOfTimer() {
        final var spanReporter = TestSpanReporter.newInstance();
        Kamon.addReporter("mySpanReporter", spanReporter);
        final var startedTimer = Timers.newTimer(testName.getMethodName())
                .tags(Map.of(
                        "foo", "bar",
                        "ping", "pong",
                        "marco", "polo"
                ))
                .start();

        final var startedSpan = underTest.startBy(startedTimer);

        final var tagsForSpanFuture = spanReporter.getTagsForSpanWithId(startedSpan.getSpanId());
        startedTimer.stop();

        assertThat(tagsForSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .isEqualTo(startedTimer.getTags());
    }

}
