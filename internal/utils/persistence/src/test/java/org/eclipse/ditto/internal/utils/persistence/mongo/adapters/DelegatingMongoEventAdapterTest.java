/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.junit.AfterClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import scala.jdk.javaapi.CollectionConverters;

/**
 * Tests {@link DelegatingMongoEventAdapter}: the BSON envelope around a delegate {@code EventSerializer}, its refusals
 * and the construction of the delegate by class name.
 */
public final class DelegatingMongoEventAdapterTest {

    private static final ExtendedActorSystem SYSTEM = (ExtendedActorSystem) ActorSystem.create(
            "DelegatingMongoEventAdapterTest", ConfigFactory.empty());

    private final RecordingEventSerializer serializer = new RecordingEventSerializer();
    private final DelegatingMongoEventAdapter underTest = new DelegatingMongoEventAdapter(serializer) {};
    private final Event<?> event = mock(Event.class);

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(SYSTEM);
    }

    @Test
    public void toJournalWrapsTheSerializerJsonAsBsonWithTheSerializerTags() {
        final Object journal = underTest.toJournal(event);

        assertThat(journal).isInstanceOf(Tagged.class);
        final Tagged tagged = (Tagged) journal;
        assertThat(tagged.payload()).isInstanceOf(BsonDocument.class)
                .isEqualTo(DittoBsonJson.getInstance().parse(RecordingEventSerializer.CANNED_JSON));
        assertThat(CollectionConverters.asJava(tagged.tags()))
                .containsExactlyInAnyOrderElementsOf(RecordingEventSerializer.TAGS);
    }

    @Test
    public void toJournalRefusesANonEvent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.toJournal("x"))
                .withMessage("Unable to toJournal a non-'Event' object! Was: " + String.class);
        assertThat(serializer.calls).isEmpty();
    }

    @Test
    public void fromJournalHandsTheDecodedJsonAndTheManifestToTheSerializer() {
        final BsonDocument stored = DittoBsonJson.getInstance().parse(RecordingEventSerializer.CANNED_JSON);

        final EventSeq result = underTest.fromJournal(stored, "m");

        assertThat(result).isSameAs(RecordingEventSerializer.RESULT);
        assertThat(serializer.receivedJson).containsExactly(RecordingEventSerializer.CANNED_JSON);
        assertThat(serializer.receivedManifests).containsExactly("m");
    }

    @Test
    public void fromJournalPassesANullManifestThrough() {
        underTest.fromJournal(DittoBsonJson.getInstance().parse(RecordingEventSerializer.CANNED_JSON), null);

        assertThat(serializer.receivedManifests).containsExactly((String) null);
    }

    @Test
    public void fromJournalRefusesANonBsonValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.fromJournal("text", "m"))
                .withMessage("Unable to fromJournal a non-'BsonValue' object! Was: " + String.class);
        assertThat(serializer.calls).isEmpty();
    }

    @Test
    public void manifestIsTheSerializerManifest() {
        assertThat(underTest.manifest(event)).isEqualTo(RecordingEventSerializer.MANIFEST);
    }

    @Test
    public void constructionByClassNameInstantiatesTheSerializer() {
        final DelegatingMongoEventAdapter byName =
                new DelegatingMongoEventAdapter(SYSTEM, RecordingEventSerializer.class.getName()) {};

        assertThat(byName.manifest(event)).isEqualTo(RecordingEventSerializer.MANIFEST);
        assertThat(byName.fromJournal(DittoBsonJson.getInstance().parse(RecordingEventSerializer.CANNED_JSON), "m"))
                .isSameAs(RecordingEventSerializer.RESULT);
    }

    @Test
    public void constructionByAnUnknownClassNameFailsWithTheName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DelegatingMongoEventAdapter(SYSTEM, "no.such.Serializer") {})
                .withMessageContaining("<no.such.Serializer>")
                .withMessageStartingWith("Could not instantiate EventSerializer")
                .withCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    public void constructionByTheNameOfANonSerializerClassFails() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DelegatingMongoEventAdapter(SYSTEM, String.class.getName()) {})
                .withMessageContaining("<java.lang.String>")
                .havingCause();
    }

}
