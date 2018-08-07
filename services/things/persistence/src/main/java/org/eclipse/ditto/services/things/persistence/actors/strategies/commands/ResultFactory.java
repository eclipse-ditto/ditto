/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * A factory for creating {@link CommandStrategy.Result} instances.
 */
@Immutable
final class ResultFactory {

    private ResultFactory() {
        throw new AssertionError();
    }

    static CommandStrategy.Result newResult(final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response) {

        return newResult(eventToPersist, response, false);
    }

    static CommandStrategy.Result newResult(final DittoRuntimeException dittoRuntimeException) {
        return new InfoResult(dittoRuntimeException);
    }

    static CommandStrategy.Result newResult(final WithDittoHeaders response) {
        return new InfoResult(response);
    }

    static CommandStrategy.Result emptyResult() {
        return new EmptyResult();
    }

    static CommandStrategy.Result newResult(final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response, final boolean becomeDeleted) {

        return new MutationResult(eventToPersist, response, becomeDeleted);
    }

    static CommandStrategy.Result newResult(final CompletionStage<WithDittoHeaders> futureResponse) {
        return new FutureInfoResult(futureResponse);
    }

    /*
     * Results are actor messages. They must be thread-safe even though some (i. e., FutureInfoResult) may not be
     * immutable.
     */
    private abstract static class AbstractResult implements CommandStrategy.Result {

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final CommandStrategy.Result that = (CommandStrategy.Result) o;
            return isBecomeDeleted() == that.isBecomeDeleted() &&
                    Objects.equals(getEventToPersist(), that.getEventToPersist()) &&
                    Objects.equals(getCommandResponse(), that.getCommandResponse()) &&
                    Objects.equals(getException(), that.getException()) &&
                    Objects.equals(getFutureResponse(), that.getFutureResponse());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getEventToPersist(), getCommandResponse(), getException(), getFutureResponse(),
                    isBecomeDeleted());
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "eventToPersist=" + getEventToPersist().orElse(null) +
                    ", response=" + getCommandResponse().orElse(null) +
                    ", exception=" + getException().orElse(null) +
                    ", futureResponse=" + getFutureResponse().orElse(null) +
                    ", becomeDeleted=" + isBecomeDeleted() +
                    "]";
        }
    }

    private static final class EmptyResult extends AbstractResult {

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, Consumer<ThingModifiedEvent>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {
            // do nothing
        }

        @Override
        public Optional<ThingModifiedEvent> getEventToPersist() {
            return Optional.empty();
        }

        @Override
        public Optional<WithDittoHeaders> getCommandResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<DittoRuntimeException> getException() {
            return Optional.empty();
        }

        @Override
        public Optional<CompletionStage<WithDittoHeaders>> getFutureResponse() {
            return Optional.empty();
        }

        @Override
        public boolean isBecomeDeleted() {
            return false;
        }
    }

    private static final class MutationResult extends AbstractResult {

        private final ThingModifiedEvent eventToPersist;
        private final WithDittoHeaders response;
        private final boolean becomeDeleted;

        private MutationResult(final ThingModifiedEvent eventToPersist,
                final WithDittoHeaders response, final boolean becomeDeleted) {
            this.eventToPersist = eventToPersist;
            this.response = response;
            this.becomeDeleted = becomeDeleted;
        }

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, Consumer<ThingModifiedEvent>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {
            persistConsumer.accept(eventToPersist, event -> {
                notifyConsumer.accept(response);
                if (becomeDeleted) {
                    becomeDeletedRunnable.run();
                }
            });
        }

        @Override
        public Optional<ThingModifiedEvent> getEventToPersist() {
            return Optional.of(eventToPersist);
        }

        @Override
        public Optional<WithDittoHeaders> getCommandResponse() {
            return Optional.of(response);
        }

        @Override
        public Optional<DittoRuntimeException> getException() {
            return Optional.empty();
        }

        @Override
        public Optional<CompletionStage<WithDittoHeaders>> getFutureResponse() {
            return Optional.empty();
        }

        @Override
        public boolean isBecomeDeleted() {
            return becomeDeleted;
        }

    }

    private static final class InfoResult extends AbstractResult {

        private final WithDittoHeaders response;

        private InfoResult(final WithDittoHeaders response) {
            this.response = response;
        }

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, Consumer<ThingModifiedEvent>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {
            notifyConsumer.accept(response);
        }

        @Override
        public Optional<ThingModifiedEvent> getEventToPersist() {
            return Optional.empty();
        }

        @Override
        public Optional<WithDittoHeaders> getCommandResponse() {
            return response instanceof DittoRuntimeException
                    ? Optional.empty()
                    : Optional.of(response);
        }

        @Override
        public Optional<DittoRuntimeException> getException() {
            return response instanceof DittoRuntimeException
                    ? Optional.of((DittoRuntimeException) response)
                    : Optional.empty();
        }

        @Override
        public Optional<CompletionStage<WithDittoHeaders>> getFutureResponse() {
            return Optional.empty();
        }

        @Override
        public boolean isBecomeDeleted() {
            return false;
        }

    }

    private static final class FutureInfoResult extends AbstractResult {

        private final CompletionStage<WithDittoHeaders> futureResponse;

        private FutureInfoResult(final CompletionStage<WithDittoHeaders> futureResponse) {
            this.futureResponse = futureResponse;
        }

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, Consumer<ThingModifiedEvent>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {
            futureResponse.thenAccept(notifyConsumer);
        }

        @Override
        public Optional<ThingModifiedEvent> getEventToPersist() {
            return Optional.empty();
        }

        @Override
        public Optional<WithDittoHeaders> getCommandResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<DittoRuntimeException> getException() {
            return Optional.empty();
        }

        @Override
        public Optional<CompletionStage<WithDittoHeaders>> getFutureResponse() {
            return Optional.of(futureResponse);
        }

        @Override
        public boolean isBecomeDeleted() {
            return false;
        }

    }
}
