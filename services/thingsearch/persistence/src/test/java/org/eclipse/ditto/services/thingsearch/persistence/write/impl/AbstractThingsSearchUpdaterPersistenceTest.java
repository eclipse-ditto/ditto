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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.thingsearch.persistence.AbstractThingSearchPersistenceTestBase;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.NotUsed;
import akka.event.LoggingAdapter;
import akka.japi.function.Function;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Test for the abstract things search updater using a dummy implementation.
 */
@RunWith(MockitoJUnitRunner.class)
public final class AbstractThingsSearchUpdaterPersistenceTest extends AbstractThingSearchPersistenceTestBase {

    @Mock
    private IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;

    @Test
    public void insertOrUpdate() throws Exception {
        final DummyThingsSearchUpdaterPersistence persistence =
                new DummyThingsSearchUpdaterPersistence(indexLengthRestrictionEnforcer);
        final Thing toSave = Thing.newBuilder().setId(":thingToSave").build();
        final Thing restricted = Thing.newBuilder().setId(":thingWithRestrictions").build();
        final long revision = 13L;
        final long policyRevision = 78L;

        when(indexLengthRestrictionEnforcer.enforceRestrictions(any(Thing.class))).thenReturn(restricted);
        // verify the persistence method sends the correct result to our dummy persistence
        persistence.mapThingUpdateResultFunction = updateResult -> restricted == updateResult.thing
                && revision == updateResult.revision
                && policyRevision == updateResult.policyRevision;

        // call persistence
        assertThat(runBlockingWithReturn(persistence.insertOrUpdate(toSave, revision, policyRevision)))
                .isEqualTo(true);

        // verify call to restriction helper
        verify(indexLengthRestrictionEnforcer).enforceRestrictions(toSave);
    }

    public static final class UpdateResult {

        private final Thing thing;
        private final long revision;
        private final long policyRevision;

        private UpdateResult(final Thing thing, final long revision, final long policyRevision) {
            this.thing = thing;
            this.revision = revision;
            this.policyRevision = policyRevision;
        }

        public boolean equals(Object other) {
            final UpdateResult o = (UpdateResult) other;
            return this.thing.equals(o.thing)
                    && this.revision == o.revision
                    && this.policyRevision == o.policyRevision;
        }
    }

    private static final class DummyThingsSearchUpdaterPersistence extends
            AbstractThingsSearchUpdaterPersistence {

        public Function<UpdateResult, Boolean> mapThingUpdateResultFunction = (u) -> true;

        /**
         * Default contructor.
         */
        DummyThingsSearchUpdaterPersistence(final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer) {
            super(Mockito.mock(LoggingAdapter.class), indexLengthRestrictionEnforcer);
        }

        @Override
        protected Source<Boolean, NotUsed> save(final Thing thing, final long revision, final long policyRevision) {
            return Source.single(true);
        }


        @Override
        PartialFunction<Throwable, Source<Boolean, NotUsed>> errorRecovery(final String thingId) {
            return null;
        }

        @Override
        public Source<Boolean, NotUsed> delete(final String thingId, final long revision) {
            return null;
        }

        @Override
        public Source<Boolean, NotUsed> delete(final String thingId) {
            return null;
        }

        @Override
        public Source<Boolean, NotUsed> executeCombinedWrites(final String thingId,
                final CombinedThingWrites combinedThingWrites) {
            return null;
        }

        @Override
        public Source<Boolean, NotUsed> updatePolicy(final Thing thing, final PolicyEnforcer policyEnforcer) {
            return null;
        }

        @Override
        public Source<Set<String>, NotUsed> getThingIdsForPolicy(final String policyId) {
            return null;
        }

        @Override
        public Source<ThingMetadata, NotUsed> getThingMetadata(final String thingId) {
            return null;
        }
    }

}