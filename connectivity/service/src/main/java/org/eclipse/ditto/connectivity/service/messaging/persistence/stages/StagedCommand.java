/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence.stages;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;

import akka.actor.ActorRef;

/**
 * Non-serializable local-only command for multi-stage processing by
 * {@link org.eclipse.ditto.connectivity.service.messaging.persistence.ConnectionPersistenceActor}.
 * <p>
 * It contains a sequence of actions. Some actions are asynchronous. The connection actor can thus schedule the next
 * action as a staged command to self after an asynchronous action. Synchronous actions can be executed right away.
 */
public final class StagedCommand implements ConnectivityCommand<StagedCommand>, Iterator<StagedCommand> {

    private final ConnectivityCommand<?> command;
    @Nullable private final ConnectivityEvent<?> event;
    private final WithDittoHeaders response;
    private final ActorRef sender;
    private final Collection<ConnectionAction> actions;

    private StagedCommand(final ConnectivityCommand<?> command,
            @Nullable final ConnectivityEvent<?> event,
            final WithDittoHeaders response,
            final ActorRef sender,
            final Collection<ConnectionAction> actions) {
        this.command = command;
        this.event = event;
        this.response = response;
        this.sender = sender;
        this.actions = actions;
    }

    /**
     * Create a staged command.
     *
     * @param command the original command.
     * @param event the event to persist.
     * @param response the response to send.
     * @param actions remaining actions.
     * @return the staged command.
     */
    public static StagedCommand of(final ConnectivityCommand<?> command, @Nullable final ConnectivityEvent<?> event,
            final WithDittoHeaders response, final List<ConnectionAction> actions) {
        return new StagedCommand(command, event, response, ActorRef.noSender(), actions);
    }

    /**
     * @return the wrapped command.
     */
    public ConnectivityCommand<?> getCommand() {
        return command;
    }

    /**
     * @return the event to persist, apply or publish or dummy-event.
     */
    public Optional<ConnectivityEvent<?>> getEvent() {
        return Optional.ofNullable(event);
    }

    /**
     * @return the response to send to the original sender, or the signal to forward to client actors.
     */
    public WithDittoHeaders getResponse() {
        return response;
    }

    /**
     * @return the original sender of a command that created this staged  command.
     */
    public ActorRef getSender() {
        return sender;
    }

    /**
     * Enhance this command with a sender unless this command has a sender already.
     *
     * @param newSender the new sender.
     * @return either an enhanced command or this command.
     */
    public StagedCommand withSenderUnlessDefined(final ActorRef newSender) {
        if (Objects.equals(ActorRef.noSender(), sender)) {
            return new StagedCommand(command, event, response, newSender, actions);
        } else {
            return this;
        }
    }

    /**
     * Return a copy of this command with a new response.
     *
     * @param response the response.
     * @return the copy.
     */
    public StagedCommand withResponse(final WithDittoHeaders response) {
        return new StagedCommand(command, event, response, sender, actions);
    }

    @Override
    public Category getCategory() {
        return command.getCategory();
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return command.getDittoHeaders();
    }

    @Override
    public StagedCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new StagedCommand(command.setDittoHeaders(dittoHeaders), event, response, sender, actions);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return command.toJson(schemaVersion, predicate);
    }

    @Override
    public String getManifest() {
        return command.getManifest();
    }

    @Override
    public String getType() {
        return command.getType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, event, response, sender, actions);
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof StagedCommand) {
            final StagedCommand that = (StagedCommand) o;
            return Objects.equals(command, that.command) &&
                    Objects.equals(event, that.event) &&
                    Objects.equals(response, that.response) &&
                    Objects.equals(sender, that.sender) &&
                    Objects.equals(actions, that.actions);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("%s[command=%s,event=%s,response=%s,sender=%s,actions=%s]",
                getClass().getSimpleName(), command, event, response, sender, actions);
    }

    @Override
    public boolean hasNext() {
        return !actions.isEmpty();
    }

    @Override
    public StagedCommand next() {
        final Queue<ConnectionAction> queue = getActionsAsQueue();
        try {
            queue.remove();
        } catch (final NoSuchElementException e) {
            throw new NoSuchElementException("Action queue did not contain more elements");
        }
        return new StagedCommand(command, event, response, sender, queue);
    }

    /**
     * Get the next action.
     *
     * @return the next action.
     * @throws java.util.NoSuchElementException unless {@code this.hasNext()}.
     */
    public ConnectionAction nextAction() {
        return actions.iterator().next();
    }

    private Queue<ConnectionAction> getActionsAsQueue() {
        return new LinkedList<>(actions);
    }
}
