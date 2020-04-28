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
package org.eclipse.ditto.services.utils.search;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.junit.After;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.utils.search.SearchSourceBuilder}.
 */
public final class SearchSourceBuilderTest {

    private ActorSystem system;

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void succeedsWithMandatoryFields() {
        system = ActorSystem.create();
        SearchSource.newBuilder()
                .dittoHeaders(DittoHeaders.empty())
                .conciergeForwarder(system.deadLetters())
                .pubSubMediator(system.deadLetters())
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void failWithMissingConciergeForwarder() {
        system = ActorSystem.create();
        SearchSource.newBuilder()
                .dittoHeaders(DittoHeaders.empty())
                .pubSubMediator(system.deadLetters())
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void failWithMissingPubSubMediator() {
        system = ActorSystem.create();
        SearchSource.newBuilder()
                .dittoHeaders(DittoHeaders.empty())
                .conciergeForwarder(system.deadLetters())
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void failWithMissingDittoHeaders() {
        system = ActorSystem.create();
        SearchSource.newBuilder()
                .conciergeForwarder(system.deadLetters())
                .pubSubMediator(system.deadLetters())
                .build();
    }

    @Test(expected = InvalidOptionException.class)
    public void failOnLimitOption() {
        SearchSource.newBuilder().options("limit(0,1)");
    }

    @Test(expected = InvalidOptionException.class)
    public void failOnCursorOption() {
        SearchSource.newBuilder().options("cursor(ABC)");
    }

    @Test(expected = InvalidOptionException.class)
    public void failOnInvalidSortFields() {
        SearchSource.newBuilder().options("sort(+invalid-sort-key)");
    }

    @Test(expected = InvalidOptionException.class)
    public void failOnInvalidSortFieldsAsOption() {
        SearchSource.newBuilder().options("sort(+invalid-sort-key)");
    }

    @Test(expected = InvalidRqlExpressionException.class)
    public void failOnInvalidFilter() {
        SearchSource.newBuilder().filter("(>_<)");
    }
}
