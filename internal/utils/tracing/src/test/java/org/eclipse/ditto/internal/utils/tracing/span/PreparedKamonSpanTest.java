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
import java.util.List;
import java.util.Map;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
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
        final var tagSet = TagSet.ofTagCollection(List.of(
                Tag.of("stringTag", "2"),
                Tag.of("longTag", 2L),
                Tag.of("booleanTag", true),
                Tag.of("doubleTag", Double.toString(2.0D))
        ));

        tagSet.forEach(underTest::tag);

        assertThat(underTest.getTagSet()).isEqualTo(tagSet);
    }

    @Test
    public void getTagsReturnsExpected() {
        final var correlationId = "12345";
        final var connectionId = "connection-1";
        final var entityId = "my-entity";

        underTest.correlationId(correlationId);
        underTest.connectionId(connectionId);
        underTest.entityId(entityId);

        assertThat(underTest.getTagSet())
                .containsOnly(
                        SpanTagKey.CORRELATION_ID.getTagForValue(correlationId),
                        SpanTagKey.CONNECTION_ID.getTagForValue(connectionId),
                        SpanTagKey.ENTITY_ID.getTagForValue(entityId)
                );
    }

    @Test
    public void nullSpanTagsAreIgnored() {
        underTest.correlationId(null);
        underTest.connectionId(null);
        underTest.entityId(null);

        assertThat(underTest.getTagSet()).isEmpty();
    }

    @Test
    public void tagsWithNullMapThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.tags(null))
                .withMessage("The tags must not be null!")
                .withNoCause();
    }

    @Test
    public void tagsPutsKeysAndValuesOfSpecifiedTagSetToTagsOfPreparedKamonSpan() {
        final var stringTagKey = "stringTag";
        final var booleanTagKey = "booleanTag";
        final var tardisTag = Tag.of(stringTagKey, "Tardis");
        final var trueBooleanTag = Tag.of(booleanTagKey, "true");
        final var stringTag = Tag.of(stringTagKey, "stringTag");
        final var booleanTag = Tag.of(booleanTagKey, false);
        final var fooTag = Tag.of("foo", "bar");
        underTest.tag(stringTag);
        underTest.tag(booleanTag);
        underTest.tag(fooTag);

        underTest.tags(TagSet.ofTagCollection(List.of(tardisTag, trueBooleanTag)));

        assertThat(underTest.getTagSet()).containsOnly(tardisTag, trueBooleanTag, fooTag);
    }

    @Test
    public void selfReturnsSelf() {
        assertThat(underTest.self()).isSameAs(underTest);
    }

    @Test
    public void startReturnsStartedSpanWithExpectedTags() {
        underTest.tags(TagSet.ofTagCollection(
                List.of(Tag.of("foo", "bar"), Tag.of("ping", "pong"), Tag.of("marco", "polo"))
        ));
        final var spanReporter = TestSpanReporter.newInstance();
        Kamon.addReporter("mySpanReporter", spanReporter);

        final var startedSpan = underTest.start();

        final var tagsForSpanFuture = spanReporter.getTagsForSpanWithId(startedSpan.getSpanId());
        startedSpan.finish();

        assertThat(tagsForSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .isEqualTo(underTest.getTagSet());
    }

    @SuppressWarnings("java:S2925")
    @Test
    public void startAtReturnsStartedSpanWithExpectedTagsAndStartInstant() throws InterruptedException {
        final var startInstant = StartInstant.now();
        underTest.tags(TagSet.ofTagCollection(
                List.of(Tag.of("foo", "bar"), Tag.of("ping", "pong"), Tag.of("marco", "polo"))
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
                    .isEqualTo(underTest.getTagSet());
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
                .tags(TagSet.ofTagCollection(
                        List.of(Tag.of("foo", "bar"), Tag.of("ping", "pong"), Tag.of("marco", "polo"))
                ))
                .start();

        final var startedSpan = underTest.startBy(startedTimer);

        final var tagsForSpanFuture = spanReporter.getTagsForSpanWithId(startedSpan.getSpanId());
        startedTimer.stop();

        assertThat(tagsForSpanFuture)
                .succeedsWithin(Duration.ofSeconds(1L))
                .isEqualTo(startedTimer.getTagSet());
    }

}
