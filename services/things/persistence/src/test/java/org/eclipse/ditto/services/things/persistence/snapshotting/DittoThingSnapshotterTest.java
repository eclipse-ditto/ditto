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
package org.eclipse.ditto.services.things.persistence.snapshotting;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActorInterface;
import org.eclipse.ditto.services.things.persistence.serializer.things.SnapshotTag;
import org.eclipse.ditto.services.things.persistence.serializer.things.ThingWithSnapshotTag;
import org.eclipse.ditto.services.utils.akka.persistence.SnapshotAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Unit test for {@link DittoThingSnapshotter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DittoThingSnapshotterTest {

    private static final ThingWithSnapshotTag THING_WITH_SNAPSHOT_TAG =
            ThingWithSnapshotTag.newInstance(THING_V1, SnapshotTag.PROTECTED);

    @Mock
    private ThingPersistenceActorInterface akkaSnapshotterMock;

    @Mock
    private SnapshotAdapter<ThingWithSnapshotTag> taggedSnapshotAdapterMock;

    private DittoThingSnapshotter underTest;

    /** */
    @Before
    public void setUp() {
        underTest = new DittoThingSnapshotter(akkaSnapshotterMock, null, taggedSnapshotAdapterMock, null,
                true, true, null, null, null);
        when(akkaSnapshotterMock.getThing()).thenReturn(THING_V1);
    }

    /** */
    @Test
    public void saveUnprotectedSnapshotWorksAsExpected() {
        underTest.doSaveSnapshot(SnapshotTag.UNPROTECTED, null, null);

        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(THING_V1, SnapshotTag.UNPROTECTED);

        Mockito.verify(taggedSnapshotAdapterMock).toSnapshotStore(eq(thingWithSnapshotTag));
        Mockito.verify(akkaSnapshotterMock).saveSnapshot(Mockito.anyObject());
    }

    /** */
    @Test
    public void saveProtectedSnapshotWorksAsExpected() {
        underTest.doSaveSnapshot(SnapshotTag.PROTECTED, null, null);

        final ThingWithSnapshotTag thingWithSnapshotTag =
                ThingWithSnapshotTag.newInstance(THING_V1, SnapshotTag.PROTECTED);

        Mockito.verify(taggedSnapshotAdapterMock).toSnapshotStore(eq(thingWithSnapshotTag));
        Mockito.verify(akkaSnapshotterMock).saveSnapshot(Mockito.anyObject());
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
