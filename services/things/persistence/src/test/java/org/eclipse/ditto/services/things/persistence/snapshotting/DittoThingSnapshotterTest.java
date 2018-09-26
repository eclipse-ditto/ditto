/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.snapshotting;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActorInterface;
import org.eclipse.ditto.services.things.persistence.serializer.SnapshotTag;
import org.eclipse.ditto.services.things.persistence.serializer.ThingWithSnapshotTag;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Unit test for {@link DittoThingSnapshotter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DittoThingSnapshotterTest {

    @Mock
    private ThingPersistenceActorInterface persistenceActorMock;

    @Mock
    private SnapshotAdapter<ThingWithSnapshotTag> taggedSnapshotAdapterMock;

    @Mock
    private DiagnosticLoggingAdapter log;

    private DittoThingSnapshotter underTest;

    /** */
    @Before
    public void setUp() {
        underTest = new DittoThingSnapshotter(persistenceActorMock, taggedSnapshotAdapterMock,
                true, true, log, null, null, null, null);
        when(persistenceActorMock.getThing()).thenReturn(THING_V1);
    }

    /** */
    @Test
    public void saveUnprotectedSnapshotWorksAsExpected() {
        underTest.doSaveSnapshot(SnapshotTag.UNPROTECTED, null, null);

        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(THING_V1, SnapshotTag.UNPROTECTED);

        Mockito.verify(taggedSnapshotAdapterMock).toSnapshotStore(eq(thingWithSnapshotTag));
        Mockito.verify(persistenceActorMock).saveSnapshot(Mockito.anyObject());
    }

    /** */
    @Test
    public void saveProtectedSnapshotWorksAsExpected() {
        underTest.doSaveSnapshot(SnapshotTag.PROTECTED, null, null);

        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(THING_V1, SnapshotTag.PROTECTED);

        Mockito.verify(taggedSnapshotAdapterMock).toSnapshotStore(eq(thingWithSnapshotTag));
        Mockito.verify(persistenceActorMock).saveSnapshot(Mockito.anyObject());
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToSaveSnapshotWithNullSnapshotTag() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.doSaveSnapshot(null, null, null))
                .withMessage("The %s must not be null!", "snapshot tag")
                .withNoCause();
    }

}
