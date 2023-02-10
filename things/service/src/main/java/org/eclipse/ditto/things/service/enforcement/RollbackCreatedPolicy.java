/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.things.service.enforcement;

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;


public record RollbackCreatedPolicy(Signal<?> initialCommand, Object response, CompletableFuture<Object> responseFuture) {

    public static RollbackCreatedPolicy of(final Signal<?> command, final Object response,
            final CompletableFuture<Object> responseFuture) {
        return new RollbackCreatedPolicy(command, response, responseFuture);
    }

    public static boolean shouldRollback(final Signal<?> command, final Object response) {
        return  command instanceof CreateThing && response instanceof CreateThingResponse thingResponse &&
                thingResponse.getResponseType() == ResponseType.ERROR;
    }

    public void completeInitialResponse() {
        if (response instanceof Throwable t) {
            responseFuture.completeExceptionally(t);
        } else {
            responseFuture.complete(response);
        }
    }
}
