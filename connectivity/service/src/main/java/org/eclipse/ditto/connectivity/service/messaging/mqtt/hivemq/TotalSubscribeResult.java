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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing.SourceSubscribeResult;

/**
 * Bundles a finite amount of {@link SourceSubscribeResult}s for determining failures.
 * TODO jff maybe nested class in ConnectionTester?
 */
final class TotalSubscribeResult {

    private final List<SourceSubscribeResult> successfulSourceSubscribeResults;
    private final List<SourceSubscribeResult> failedSourceSubscribeResults;

    private TotalSubscribeResult(final List<SourceSubscribeResult> successfulSourceSubscribeResults,
            final List<SourceSubscribeResult> failedSourceSubscribeResults) {

        this.successfulSourceSubscribeResults = List.copyOf(successfulSourceSubscribeResults);
        this.failedSourceSubscribeResults = List.copyOf(failedSourceSubscribeResults);
    }

    /**
     * Returns an instance of {@code TotalSubscribeResult} for the specified list argument.
     *
     * @param sourceSubscribeResults a list of {@code SourceSubscribeResult}s which are regarded as a whole to represent
     * a total subscribe result.
     * @return the instance.
     * @throws NullPointerException if {@code sourceSubscribeResults} is {@code null}.
     */
     static TotalSubscribeResult of(final List<SourceSubscribeResult> sourceSubscribeResults) {
        ConditionChecker.checkNotNull(sourceSubscribeResults, "sourceSubscribeResults");
        final var sourceSubscribeResultsByIsSuccess =
                sourceSubscribeResults.stream().collect(Collectors.partitioningBy(SourceSubscribeResult::isSuccess));
        return new TotalSubscribeResult(sourceSubscribeResultsByIsSuccess.get(true),
                sourceSubscribeResultsByIsSuccess.get(false));
    }

    boolean hasFailures() {
        return !failedSourceSubscribeResults.isEmpty();
    }

    Stream<SourceSubscribeResult> successfulSourceSubscribeResults() {
        return successfulSourceSubscribeResults.stream();
    }

    Stream<SourceSubscribeResult> failedSourceSubscribeResults() {
        return failedSourceSubscribeResults.stream();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (TotalSubscribeResult) o;
        return Objects.equals(successfulSourceSubscribeResults, that.successfulSourceSubscribeResults) &&
                Objects.equals(failedSourceSubscribeResults, that.failedSourceSubscribeResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successfulSourceSubscribeResults, failedSourceSubscribeResults);
    }

}
