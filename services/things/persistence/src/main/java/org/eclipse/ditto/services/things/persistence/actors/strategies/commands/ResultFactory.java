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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.headers.conditional.ETagValueGenerator;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

/**
 * A factory for creating {@link CommandStrategy.Result} instances.
 */
@Immutable
final class ResultFactory {

    private ResultFactory() {
        throw new AssertionError();
    }

    static CommandStrategy.Result newMutationResult(final ThingModifyCommand command,
            final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response, final ETagEntityProvider eTagEntityProvider) {

        return new MutationResult(command, eventToPersist, response, false, eTagEntityProvider);
    }

    static CommandStrategy.Result newMutationResult(final ThingModifyCommand command,
            final ThingModifiedEvent eventToPersist,
            final ThingCommandResponse response, final boolean becomeDeleted, final ETagEntityProvider eTagProvider) {

        return new MutationResult(command, eventToPersist, response, becomeDeleted, eTagProvider);
    }

    static CommandStrategy.Result newErrorResult(final DittoRuntimeException dittoRuntimeException) {
        return new DittoRuntimeExceptionResult(dittoRuntimeException);
    }

    static CommandStrategy.Result newQueryResult(final Command command, @Nullable final Thing completeThing,
            final WithDittoHeaders response, @Nullable final ETagEntityProvider eTagEntityProvider) {

        return new InfoResult(command, completeThing, response, eTagEntityProvider);
    }

    static CommandStrategy.Result emptyResult() {
        return EmptyResult.INSTANCE;
    }

    static CommandStrategy.Result newFutureResult(final CompletionStage<WithDittoHeaders> futureResponse) {
        return new FutureInfoResult(futureResponse);
    }


    private static WithDittoHeaders appendETagHeaderIfProvided(final Command command,
            final WithDittoHeaders withDittoHeaders, @Nullable final Thing thing,
            @Nullable final ETagEntityProvider eTagProvider) {
        if (eTagProvider == null) {
            return withDittoHeaders;
        }

        @SuppressWarnings("unchecked")
        final Optional<Object> eTagEntityOpt = eTagProvider.determineETagEntity(command, thing);
        if (eTagEntityOpt.isPresent()) {
            final Optional<CharSequence> eTagValueOpt = ETagValueGenerator.generate(eTagEntityOpt.get());
            if (eTagValueOpt.isPresent())  {
                final CharSequence eTagValue = eTagValueOpt.get();
                final DittoHeaders newDittoHeaders = withDittoHeaders.getDittoHeaders().toBuilder()
                        .eTag(eTagValue)
                        .build();
                return withDittoHeaders.setDittoHeaders(newDittoHeaders);
            }
        }
        return withDittoHeaders;
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
        private static final EmptyResult INSTANCE = new EmptyResult();

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
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
        private final ThingModifyCommand command;
        private final ThingModifiedEvent eventToPersist;
        private final WithDittoHeaders response;
        private final boolean becomeDeleted;
        @Nullable
        private final ETagEntityProvider eTagProvider;

        private MutationResult(final ThingModifyCommand command, final ThingModifiedEvent eventToPersist,
                final WithDittoHeaders response, final boolean becomeDeleted,
                @Nullable final ETagEntityProvider eTagProvider) {
            this.command = command;
            this.eventToPersist = eventToPersist;
            this.response = response;
            this.becomeDeleted = becomeDeleted;
            this.eTagProvider = eTagProvider;
        }

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {
            persistConsumer.accept(eventToPersist, (event, resultingThing) -> {
                final WithDittoHeaders notificationResponse =
                        appendETagHeaderIfProvided(command, response, resultingThing, eTagProvider);
                notifyConsumer.accept(notificationResponse);
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
        private final Command command;
        private final WithDittoHeaders response;
        @Nullable
        private final Thing completeThing;
        @Nullable
        private final ETagEntityProvider eTagEntityProvider;

        private InfoResult(final Command command, @Nullable final Thing completeThing,
                final WithDittoHeaders response,
                @Nullable final ETagEntityProvider eTagEntityProvider) {

            this.command = command;
            this.completeThing = completeThing;
            this.response = response;
            this.eTagEntityProvider = eTagEntityProvider;
        }

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {

            final WithDittoHeaders notificationResponse =
                    appendETagHeaderIfProvided(command, response, completeThing, eTagEntityProvider);
            notifyConsumer.accept(notificationResponse);
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

    private static final class DittoRuntimeExceptionResult extends AbstractResult {
        private final DittoRuntimeException dittoRuntimeException;

        private DittoRuntimeExceptionResult(final DittoRuntimeException dittoRuntimeException) {
            this.dittoRuntimeException = dittoRuntimeException;
        }

        @Override
        public void apply(final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
                final Consumer<WithDittoHeaders> notifyConsumer, final Runnable becomeDeletedRunnable) {

            notifyConsumer.accept(dittoRuntimeException);
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
            return Optional.of(dittoRuntimeException);
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
        public void apply(final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistConsumer,
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
