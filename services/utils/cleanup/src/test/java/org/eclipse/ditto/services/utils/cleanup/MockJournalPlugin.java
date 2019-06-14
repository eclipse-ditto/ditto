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
package org.eclipse.ditto.services.utils.cleanup;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.persistence.AtomicWrite;
import akka.persistence.PersistentRepr;
import akka.persistence.journal.japi.AsyncWriteJournal;
import scala.concurrent.Future;

/**
 * Mock implementation of {@link AsyncWriteJournal} that logs invocations and forwards invocations to delete* methods
 * to another implementation e.g. a Mockito mock to verify invocations.
 */
public class MockJournalPlugin extends AsyncWriteJournal {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockJournalPlugin.class);

    @Nullable private static AsyncWriteJournal mock = null;

    static void setMock(final AsyncWriteJournal journal) {
        mock = journal;
    }

    @Override
    public Future<Void> doAsyncReplayMessages(final String persistenceId, final long fromSequenceNr,
            final long toSequenceNr,
            final long max, final Consumer<PersistentRepr> replayCallback) {
        LOGGER.debug("[doAsyncReplayMessages]: persistenceId {}, fromSequenceNr {}, toSequenceNr {}, max {}",
                persistenceId,
                fromSequenceNr, toSequenceNr, max);
        return Future.successful(null);
    }

    @Override
    public Future<Long> doAsyncReadHighestSequenceNr(final String persistenceId, final long fromSequenceNr) {
        LOGGER.debug("[doAsyncReadHighestSequenceNr]: persistenceId {}, fromSequenceNr {}", persistenceId,
                fromSequenceNr);
        return Future.successful(0L);
    }

    @Override
    public Future<Iterable<Optional<Exception>>> doAsyncWriteMessages(final Iterable<AtomicWrite> messages) {
        LOGGER.debug("[doAsyncWriteMessages]: messages {}", messages);
        final Iterable<Optional<Exception>> collect =
                Stream.of(messages).map(m -> Optional.<Exception>empty()).collect(Collectors.toList());
        return Future.successful(collect);
    }

    @Override
    public Future<Void> doAsyncDeleteMessagesTo(final String persistenceId, final long toSequenceNr) {
        LOGGER.debug("[doAsyncDeleteMessagesTo]: persistenceId {}, toSequenceNr {}", persistenceId, toSequenceNr);
        return getMock().doAsyncDeleteMessagesTo(persistenceId, toSequenceNr);
    }

    private AsyncWriteJournal getMock() {
        return Optional.ofNullable(mock).orElseThrow(() -> new IllegalStateException("Forgot to set mock?"));
    }
}
