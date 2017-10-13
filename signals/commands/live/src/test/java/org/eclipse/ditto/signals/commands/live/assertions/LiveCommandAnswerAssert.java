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
package org.eclipse.ditto.signals.commands.live.assertions;

import java.util.Optional;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.assertions.CommandAssertions;
import org.eclipse.ditto.signals.commands.base.assertions.CommandResponseAssert;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.eclipse.ditto.signals.events.things.assertions.ThingEventAssertions;
import org.eclipse.ditto.signals.events.things.assertions.ThingModifiedEventAssert;

/**
 * An assert for {@link LiveCommandAnswer}s.
 */
public class LiveCommandAnswerAssert extends AbstractAssert<LiveCommandAnswerAssert, LiveCommandAnswer> {

    /**
     * Constructs a new {@code LiveCommandAnswerAssert} object.
     *
     * @param actual the live command answer to be checked.
     */
    public LiveCommandAnswerAssert(final LiveCommandAnswer actual) {
        super(actual, LiveCommandAnswerAssert.class);
    }

    public LiveCommandAnswerAssert hasNoEvent() {
        isNotNull();
        final Optional<Event> actualEvent = actual.getEvent();
        Assertions.assertThat(actualEvent)
                .overridingErrorMessage("Expected LiveCommandAnswer not to have an event but it had\n<%s>",
                        actualEvent.orElse(null))
                .isEmpty();
        return myself;
    }

    public LiveCommandAnswerAssert hasEvent(final Event<?> expectedEvent) {
        isNotNull();
        final Optional<Event> actualEvent = actual.getEvent();
        Assertions.assertThat(actualEvent)
                .overridingErrorMessage("Expected LiveCommandAnswer to have event\n<%s> but it had\n<%s>",
                        expectedEvent, actualEvent.orElse(null))
                .contains(expectedEvent);
        return myself;
    }

    public LiveCommandAnswerAssert hasResponse(final CommandResponse<?> expectedCommandResponse) {
        isNotNull();
        final Optional<CommandResponse> actualResponse = actual.getResponse();
        Assertions.assertThat(actualResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer to have response\n<%s> but it had\n<%s>",
                        expectedCommandResponse, actualResponse.orElse(null))
                .contains(expectedCommandResponse);
        return myself;
    }

    public CommandResponseAssert hasThingModifyCommandResponse() {
        isNotNull();
        final Optional<CommandResponse> actualResponse = actual.getResponse();
        Assertions.assertThat(actualResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer to have a response but it had none")
                .isPresent();

        final CommandResponse commandResponse = actualResponse.orElse(null);
        Assertions.assertThat(commandResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer has a\n<ThingModifyCommandResponse> but it " +
                        "had a\n<%s>", commandResponse.getClass().getSimpleName())
                .isInstanceOf(ThingModifyCommandResponse.class);

        return CommandAssertions.assertThat((ThingModifyCommandResponse) commandResponse);
    }

    public ThingErrorResponseAssert hasThingErrorResponse() {
        isNotNull();
        final Optional<CommandResponse> actualResponse = actual.getResponse();
        Assertions.assertThat(actualResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer to have a response but it had none")
                .isPresent();

        final CommandResponse commandResponse = actualResponse.orElse(null);
        Assertions.assertThat(commandResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer has a\n<ThingErrorResponse> but it " +
                        "had a\n<%s>", commandResponse.getClass().getSimpleName())
                .isInstanceOf(ThingErrorResponse.class);

        return new ThingErrorResponseAssert((ThingErrorResponse) commandResponse);
    }

    public LiveCommandAnswerAssert hasNoResponse() {
        isNotNull();
        final Optional<CommandResponse> actualResponse = actual.getResponse();
        Assertions.assertThat(actualResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer not to have a response but it had\n<%s>",
                        actualResponse.orElse(null))
                .isEmpty();
        return myself;
    }

    public CommandResponseAssert hasThingQueryCommandResponse() {
        isNotNull();
        final Optional<CommandResponse> actualResponse = actual.getResponse();
        Assertions.assertThat(actualResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer to have a response but it had none")
                .isPresent();

        final CommandResponse commandResponse = actualResponse.orElse(null);
        Assertions.assertThat(commandResponse)
                .overridingErrorMessage("Expected LiveCommandAnswer has a\n<ThingQueryCommandResponse> but it " +
                        "had a\n<%s>", commandResponse.getClass().getSimpleName())
                .isInstanceOf(ThingQueryCommandResponse.class);

        return CommandAssertions.assertThat((ThingQueryCommandResponse) commandResponse);
    }

    public ThingModifiedEventAssert hasThingModifiedEvent() {
        isNotNull();
        final Optional<Event> actualEvent = actual.getEvent();
        Assertions.assertThat(actualEvent)
                .overridingErrorMessage("Expected LiveCommandAnswer to have an event but it had none")
                .isPresent();

        final Event event = actualEvent.orElse(null);
        Assertions.assertThat(event)
                .overridingErrorMessage("Expected LiveCommandAnswer has a\n<ThingModifiedEvent> but it " +
                        "had a\n<%s>", event.getClass().getSimpleName())
                .isInstanceOf(ThingModifiedEvent.class);

        return ThingEventAssertions.assertThat((ThingModifiedEvent) event);
    }

}
