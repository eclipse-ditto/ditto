/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Optional;

import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;

import akka.NotUsed;
import akka.actor.AbstractFSMWithStash;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;

/**
 * This Actor initiates persistence updates related to 1 thing.
 */
public final class ThingUpdater extends AbstractFSMWithStash<ThingUpdater.State, ThingUpdater.Data> {

    private final ThingId thingId;
    private final Flow<MongoWriteModel, Result, NotUsed> flow;
    private final Materializer materializer;

    // TODO
    public record Data(Metadata metadata, Optional<AbstractWriteModel> lastWriteModel) {}

    // TODO
    public record Result(MongoWriteModel mongoWriteModel, WriteResultAndErrors resultAndErrors) {}

    enum State {
        RECOVERING,
        READY,
        PERSISTING
    }

    private ThingUpdater(final Flow<MongoWriteModel, ThingUpdater.Result, NotUsed> flow) {
        thingId = tryToGetThingId();
        this.flow = flow;
        this.materializer = Materializer.createMaterializer(getContext());
    }

    @Override
    public void postStop() {
        // wrap exception in RuntimeException for java type checker
        try {
            super.postStop();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ThingId tryToGetThingId() {
        final Charset utf8 = StandardCharsets.UTF_8;
        try {
            return getThingId(utf8);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(MessageFormat.format("Charset <{0}> is unsupported!", utf8.name()), e);
        }
    }

    private ThingId getThingId(final Charset charset) throws UnsupportedEncodingException {
        final String actorName = self().path().name();
        return ThingId.of(URLDecoder.decode(actorName, charset.name()));
    }
}
