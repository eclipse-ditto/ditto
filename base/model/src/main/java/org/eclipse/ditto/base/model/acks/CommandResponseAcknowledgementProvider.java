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

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Provides {@link Acknowledgement}s based on {@link CommandResponse}s abstracting away model-specific dependencies.
 *
 * @param <R> the type of the command response which to provide the Acknowledgements for.
 * @since 3.0.0
 */
@Immutable
public interface CommandResponseAcknowledgementProvider<R extends CommandResponse<? extends R>> {

    /**
     * Provides an {@link Acknowledgement} based on the provided {@code originalSignal} and {@code commandResponse}.
     *
     * @param originatingSignal the Signal which caused the passed in {@code commandResponse}.
     * @param commandResponse the CommandResponse to provide the Acknowledgement for.
     * @return the created Acknowledgement.
     */
    Acknowledgement provideAcknowledgement(Signal<?> originatingSignal, R commandResponse);

    /**
     * Checks if the passed {@code commandResponse} is applicable by this provider to provide Acknowledgements for.
     *
     * @param commandResponse the commandResponse to check.
     * @return whether the commandResponse is applicable for this provider to provide Acknowledgements for.
     * @throws NullPointerException if the passed {@code commandResponse} was {@code null}.
     */
    boolean isApplicable(R commandResponse);

    /**
     * Get the class of the type of command responses this provider handles.
     *
     * @return the class of the command response.
     */
    Class<R> getMatchedClass();


}
