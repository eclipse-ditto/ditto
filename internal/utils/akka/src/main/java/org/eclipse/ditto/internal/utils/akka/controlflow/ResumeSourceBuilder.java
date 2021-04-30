/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Builder of the resume source.
 *
 * @since 1.1.0
 */
public final class ResumeSourceBuilder<S, E> {

    Duration minBackoff = Duration.ofSeconds(1L);
    Duration maxBackoff = Duration.ofMinutes(2L);
    int maxRestarts = -1;
    Duration recovery = Duration.ofMinutes(5L);
    S initialSeed;
    Function<S, Source<E, ?>> resume;
    int lookBehind = 1;
    Function<List<E>, S> nextSeed;
    Function<Throwable, Optional<Throwable>> mapError = error -> Optional.empty();

    ResumeSourceBuilder() {}

    /**
     * Create a source that resumes based on a seed computed from the final N elements that passed through the stream.
     * Resumption is delayed by exponential backoff with a fixed recovery interval.
     * <ul>
     * <li>Emits when: stream created by {@code resume} emits.</li>
     * <li>Completes when: a stream created by {@code resume} completes.</li>
     * <li>Cancels when: downstream cancels.</li>
     * <li>Fails when: more than {@code maxRestarts} streams created by {@code resume} failed with a recoverable
     * error, or any stream created by {@code resume} failed with an unrecoverable error. Whether an exception
     * is recoverable is determined by {@code mapError}.</li>
     * </ul>
     *
     * @return the resume-source.
     */
    public Source<E, NotUsed> build() {
        checkNotNull(minBackoff, "minBackoff");
        checkNotNull(maxBackoff, "maxBackoff");
        checkNotNull(recovery, "recovery");
        checkNotNull(initialSeed, "initialSeed");
        checkNotNull(resume, "resume");
        checkNotNull(nextSeed, "nextSeed");
        checkNotNull(mapError, "mapError");
        return ResumeSource.build(this);
    }

    /**
     * Set the minimum backoff after a recoverable error.
     *
     * @param minBackoff the minimum backoff.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> minBackoff(final Duration minBackoff) {
        this.minBackoff = minBackoff;
        return this;
    }

    /**
     * Set the maximum backoff after a recoverable error.
     *
     * @param maxBackoff the maximum backoff.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> maxBackoff(final Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
        return this;
    }

    /**
     * Set the maximum number of restarts before failing the stream. 0 means no recovery and a negative number
     * means unlimited recovery.
     *
     * @param maxRestarts the number of restarts to attempt.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> maxRestarts(final int maxRestarts) {
        this.maxRestarts = maxRestarts;
        return this;
    }

    /**
     * Set the recovery period. If so much time passes without error then the backoff is reset.
     *
     * @param recovery the recovery period.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> recovery(final Duration recovery) {
        this.recovery = recovery;
        return this;
    }

    /**
     * Set the initial seed to start the stream.
     *
     * @param initialSeed the seed.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> initialSeed(final S initialSeed) {
        this.initialSeed = initialSeed;
        return this;
    }

    /**
     * Create or resume a stream from a seed.
     *
     * @param resume the resumption function.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> resume(final Function<S, Source<E, ?>> resume) {
        this.resume = resume;
        return this;
    }

    /**
     * Set how many stream elements to keep for resumption.
     *
     * @param lookBehind the number of elements to keep.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> lookBehind(final int lookBehind) {
        this.lookBehind = lookBehind;
        return this;
    }

    /**
     * Compute the next seed from the last N elements to pass through the stream.
     *
     * @param nextSeed the function to compute the next seed.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> nextSeed(final Function<List<E>, S> nextSeed) {
        this.nextSeed = nextSeed;
        return this;
    }

    /**
     * Set a function that maps recoverable errors to the empty optional and non-recoverable errors to
     * errors the source should fail with.
     * If not set, all errors are considered recoverable.
     *
     * @param mapError the function.
     * @return this builder.
     */
    public ResumeSourceBuilder<S, E> mapError(final Function<Throwable, Optional<Throwable>> mapError) {
        this.mapError = mapError;
        return this;
    }
}
