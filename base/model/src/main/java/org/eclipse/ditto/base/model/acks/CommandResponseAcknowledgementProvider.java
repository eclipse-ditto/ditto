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
package org.eclipse.ditto.base.model.acks;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Provides {@link Acknowledgement}s based on {@link CommandResponse}s abstracting away model-specific dependencies.
 *
 * @param <C> the type of the command for which to provide the Acknowledgement.
 * @since 3.0.0
 */
@Immutable
public interface CommandResponseAcknowledgementProvider<C extends Command<?>> {

    /**
     * Provides an {@link Acknowledgement} based on the provided {@code originalSignal} and {@code commandResponse}.
     *
     * @param originatingSignal the Signal which caused the passed in {@code commandResponse}.
     * @param commandResponse the CommandResponse to provide the Acknowledgement for.
     * @return the created Acknowledgement.
     */
    Acknowledgement provideAcknowledgement(C originatingSignal, CommandResponse<?> commandResponse);

    /**
     * Checks if the passed {@code commandResponse} is applicable by this provider to provide Acknowledgements for.
     *
     * @param commandResponse the commandResponse to check.
     * @return whether the commandResponse is applicable for this provider to provide Acknowledgements for.
     * @throws NullPointerException if the passed {@code commandResponse} was {@code null}.
     */
    boolean isApplicable(CommandResponse<?> commandResponse);

    /**
     * Get the class of the type of commands this provider handles.
     *
     * @return the class of the command.
     */
    Class<?> getCommandClass();

}
