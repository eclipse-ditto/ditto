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
package org.eclipse.ditto.internal.utils.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.Test;

/**
 * Unit tests for {@link ReadableSignalView} as created by {@link PartialAccessSignalFilter#viewFor}.
 */
public final class ReadableSignalViewTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto.test", "thing");
    private static final AuthorizationSubject ALICE = AuthorizationSubject.newInstance("test:alice");
    private static final AuthorizationSubject GROUP_X = AuthorizationSubject.newInstance("test:group-x");
    private static final AuthorizationSubject CAROL = AuthorizationSubject.newInstance("test:carol");
    private static final AuthorizationSubject BOB = AuthorizationSubject.newInstance("test:bob");
    private static final Attributes ATTRIBUTES = Attributes.newBuilder().set("pub", "p").set("secret", "s").build();
    private static final ProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private static final String REVOKED_SECRET_FOR_GROUP_X =
            "{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}";
    private static final DittoHeaders REVOKED_FOR_GROUP_X = revokedHeaders(REVOKED_SECRET_FOR_GROUP_X);
    private static final DittoHeaders PARTIAL_FOR_CAROL = DittoHeaders.newBuilder()
            .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                    "{\"subjects\":[\"test:carol\"],\"paths\":{\"attributes/pub\":[0]}}")
            .readGrantedSubjects(List.of(CAROL))
            .build();

    @Test
    public void unrestrictedSubscriberGetsTheSameSignalInstance() {
        final AttributesModified withoutHeaders = attributesModified(DittoHeaders.empty());
        final AttributesModified withHeaders = attributesModified(REVOKED_FOR_GROUP_X);

        final ReadableSignalView noHeaders = view(withoutHeaders, context(ALICE, GROUP_X));
        final ReadableSignalView notConcerned = view(withHeaders, context(ALICE));

        assertThat(noHeaders.getSignal()).isSameAs(withoutHeaders);
        assertThat(noHeaders.isRestricted()).isFalse();
        assertThat(notConcerned.getSignal()).isSameAs(withHeaders);
        assertThat(notConcerned.isRestricted()).isFalse();
        final JsonObject extra = JsonObject.newBuilder().set("attributes", ATTRIBUTES.toJson()).build();
        assertThat(notConcerned.filterThingJson(extra)).isSameAs(extra);
        assertThat(view(withoutHeaders, null).getSignal()).isSameAs(withoutHeaders);
        assertThat(PartialAccessSignalFilter.viewFor(withHeaders, null)).isEmpty();
    }

    @Test
    public void mergeWhichOnlyTouchedARevokedFieldIsNotDelivered() {
        final ThingMerged merge = ThingMerged.of(THING_ID, JsonPointer.empty(),
                JsonObject.of("{\"attributes\":{\"secret\":null}}"), 2L, Instant.EPOCH, REVOKED_FOR_GROUP_X, null);
        final ThingMerged attributesMerge = ThingMerged.of(THING_ID, JsonPointer.of("/attributes"),
                JsonObject.of("{\"secret\":{\"nested\":1}}"), 2L, Instant.EPOCH, REVOKED_FOR_GROUP_X, null);

        assertThat(PartialAccessSignalFilter.viewFor(merge, context(ALICE, GROUP_X))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(attributesMerge, context(ALICE, GROUP_X))).isEmpty();
        assertThat(view(merge, context(ALICE)).getSignal()).isSameAs(merge);
    }

    @Test
    public void mergeOfAPartiallyUnreadableObjectIsNotDeliveredToAPartialReader() {
        final ThingMerged merge = ThingMerged.of(THING_ID, JsonPointer.empty(),
                JsonObject.of("{\"attributes\":{\"secret\":null}}"), 2L, Instant.EPOCH, PARTIAL_FOR_CAROL, null);

        assertThat(PartialAccessSignalFilter.viewFor(merge, context(CAROL))).isEmpty();
    }

    @Test
    public void metadataIsFilteredWithTheSamePathsAsTheValue() {
        final JsonObject metadata = JsonObject.of("{\"pub\":{\"unit\":\"m\"},\"secret\":{\"issuer\":\"s\"}}");
        final ThingMerged merge = ThingMerged.of(THING_ID, JsonPointer.of("/attributes"), ATTRIBUTES.toJson(), 2L,
                Instant.EPOCH, REVOKED_FOR_GROUP_X, null);
        final Adaptable withMetadata = withMetadata(merge, metadata, context(ALICE, GROUP_X));
        final Adaptable partialWithMetadata =
                withMetadata(merge.setDittoHeaders(PARTIAL_FOR_CAROL), metadata, context(CAROL));
        final Adaptable onlySecretMetadata = withMetadata(merge,
                JsonObject.of("{\"secret\":{\"issuer\":\"s\"}}"), context(ALICE, GROUP_X));

        final Metadata readableMetadata = Metadata.newMetadata(JsonObject.of("{\"pub\":{\"unit\":\"m\"}}"));
        assertThat(SubscriberFilters.filter(withMetadata, context(ALICE, GROUP_X))
                .getPayload().getMetadata()).contains(readableMetadata);
        assertThat(SubscriberFilters.filter(partialWithMetadata, context(CAROL))
                .getPayload().getMetadata()).contains(readableMetadata);
        assertThat(SubscriberFilters.filter(onlySecretMetadata,
                context(ALICE, GROUP_X)).getPayload().getMetadata()).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(merge, withMetadata, context(ALICE, GROUP_X)).orElseThrow()
                .getAdaptable().getPayload().getMetadata()).contains(readableMetadata);
    }

    @Test
    public void metadataOfARevokedFieldIsRemovedFromThingJson() {
        final ReadableSignalView view = view(attributesModified(REVOKED_FOR_GROUP_X), context(ALICE, GROUP_X));
        final JsonObject extra = JsonObject.of("{\"thingId\":\"" + THING_ID + "\",\"attributes\":" +
                ATTRIBUTES.toJson() + ",\"_metadata\":{\"attributes\":{\"secret\":{\"issuer\":\"s\"}}}}");

        assertThat(view.filterThingJson(extra)).isEqualTo(JsonObject.of(
                "{\"thingId\":\"" + THING_ID + "\",\"attributes\":{\"pub\":\"p\"}}"));
    }

    @Test
    public void thingJsonIsFilteredByPartialAndRevokedPaths() {
        final DittoHeaders headers = PARTIAL_FOR_CAROL.toBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:carol\"],\"paths\":{\"attributes\":[0]}}")
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[0]}}")
                .build();
        final ReadableSignalView view = view(attributesModified(headers), context(CAROL, GROUP_X));
        final JsonObject extra = JsonObject.of("{\"thingId\":\"" + THING_ID + "\",\"policyId\":\"p:p\"," +
                "\"attributes\":" + ATTRIBUTES.toJson() + ",\"features\":{\"f\":{}}}");

        assertThat(view.filterThingJson(extra)).isEqualTo(JsonObject.of(
                "{\"thingId\":\"" + THING_ID + "\",\"attributes\":{\"pub\":\"p\"}}"));
    }

    @Test
    public void malformedRevokedHeaderFailsClosed() {
        final AttributesModified truncated = attributesModified(trustedHeaders(
                DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(), REVOKED_SECRET_FOR_GROUP_X.substring(0, 20)));
        final AttributesModified indexOutOfRange = attributesModified(revokedHeaders(
                "{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/secret\":[1]}}"));
        final AttributesModified withoutSubjects = attributesModified(revokedHeaders(
                "{\"paths\":{\"attributes/secret\":[0]}}"));

        assertThat(PartialAccessSignalFilter.viewFor(truncated, context(ALICE, GROUP_X))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(truncated, context(ALICE))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(indexOutOfRange, context(ALICE, GROUP_X))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(withoutSubjects, context(ALICE, GROUP_X))).isEmpty();
    }

    @Test
    public void malformedPartialHeaderFailsClosed() {
        final AttributesModified truncated = attributesModified(trustedHeaders(
                DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), "{\"subjects\":"));
        final AttributesModified notAnObject = attributesModified(trustedHeaders(
                DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(), "[\"test:carol\"]"));

        assertThat(PartialAccessSignalFilter.viewFor(truncated, context(CAROL))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(notAnObject, context(CAROL))).isEmpty();
    }

    @Test
    public void partialSubjectWithoutPathsIsTolerated() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:carol\",\"test:nothing\"],\"paths\":{\"attributes/pub\":[0]}}")
                .readGrantedSubjects(List.of(CAROL))
                .build();

        final Signal<?> readable = view(attributesModified(headers), context(CAROL)).getSignal();

        assertThat(((AttributesModified) readable).getModifiedAttributes())
                .isEqualTo(Attributes.newBuilder().set("pub", "p").build());
    }

    @Test
    public void rootRevokedSubscriberGetsOnlyTheThingIdAndNoEmptiedRootEvents() {
        final DittoHeaders rootRevoked = revokedHeaders(
                "{\"subjects\":[\"test:group-x\"],\"paths\":{\"thingId\":[0],\"attributes/secret\":[0]}," +
                        "\"root\":[0]}");
        final Thing thing = Thing.newBuilder().setId(THING_ID).setAttributes(ATTRIBUTES).build();
        final ThingModified thingModified = ThingModified.of(thing, 2L, Instant.EPOCH, rootRevoked, null);
        final ThingDeleted thingDeleted = ThingDeleted.of(THING_ID, 3L, Instant.EPOCH, rootRevoked, null);
        final ThingMerged onlyIdentity = ThingMerged.of(THING_ID, JsonPointer.empty(),
                JsonObject.of("{\"thingId\":\"" + THING_ID + "\"}"), 4L, Instant.EPOCH, rootRevoked, null);

        final ReadableSignalView view = view(thingModified, context(ALICE, GROUP_X));

        final JsonObject readable =
                JsonObject.of("{\"thingId\":\"" + THING_ID + "\",\"attributes\":{\"pub\":\"p\"}}");
        assertThat(view.getAdaptable().getPayload().getValue()).contains(readable);
        assertThat(((ThingModified) view.getSignal()).getThing().toJson()).isEqualTo(readable);
        assertThat(view.filterThingJson(thing.toJson())).isEqualTo(readable);
        assertThat(PartialAccessSignalFilter.viewFor(thingDeleted, context(ALICE, GROUP_X))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(onlyIdentity, context(ALICE, GROUP_X))).isEmpty();
        assertThat(view(thingDeleted, context(ALICE)).getSignal()).isSameAs(thingDeleted);
        assertThat(view(onlyIdentity, context(ALICE)).getSignal()).isSameAs(onlyIdentity);
    }

    @Test
    public void liveCommandWithAnUnreadablePartIsNotDelivered() {
        final DittoHeaders liveRevoked = REVOKED_FOR_GROUP_X.toBuilder().channel("live").build();
        final ModifyAttributes modifyAttributes = ModifyAttributes.of(THING_ID, ATTRIBUTES, liveRevoked);
        final ModifyAttributes onlySecret = ModifyAttributes.of(THING_ID,
                Attributes.newBuilder().set("secret", "s").build(), liveRevoked);

        assertThat(PartialAccessSignalFilter.viewFor(modifyAttributes, context(ALICE, GROUP_X))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(onlySecret, context(ALICE, GROUP_X))).isEmpty();
        assertThat(view(modifyAttributes, context(ALICE)).getSignal()).isSameAs(modifyAttributes);
    }

    @Test
    public void liveCommandWhichIsCompletelyReadableIsDeliveredUnchanged() {
        final ModifyAttributes onlyPub = ModifyAttributes.of(THING_ID,
                Attributes.newBuilder().set("pub", "p").build(),
                REVOKED_FOR_GROUP_X.toBuilder().channel("live").build());
        final ModifyAttributes onlyPubForCarol = onlyPub.setDittoHeaders(
                PARTIAL_FOR_CAROL.toBuilder().channel("live").build());

        final ReadableSignalView view = view(onlyPub, context(ALICE, GROUP_X));

        assertThat(view.getSignal()).isSameAs(onlyPub);
        assertThat(view.getAdaptable().getPayload().getValue()).contains(onlyPub.getEntity().orElseThrow());
        assertThat(view(onlyPubForCarol, context(CAROL)).getSignal()).isSameAs(onlyPubForCarol);
    }

    @Test
    public void liveCommandIsNotDeliveredToSingleSubjectWithRevokeBelowItsGrant() {
        final DittoHeaders bobRevoked = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"test:bob\"],\"paths\":{\"attributes/secret\":[0]}}")
                .readGrantedSubjects(List.of(BOB))
                .channel("live")
                .build();
        final ModifyAttributes modifyAttributes = ModifyAttributes.of(THING_ID, ATTRIBUTES, bobRevoked);
        final ModifyAttributes partialForCarol = modifyAttributes.setDittoHeaders(
                PARTIAL_FOR_CAROL.toBuilder().channel("live").build());

        assertThat(PartialAccessSignalFilter.viewFor(modifyAttributes, context(BOB))).isEmpty();
        assertThat(PartialAccessSignalFilter.viewFor(partialForCarol, context(CAROL))).isEmpty();
    }

    @Test
    public void retrieveAndTwinCommandsAreNotFiltered() {
        final DittoHeaders liveRevoked = REVOKED_FOR_GROUP_X.toBuilder().channel("live").build();
        final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID, liveRevoked);
        final ModifyAttributes twinCommand = ModifyAttributes.of(THING_ID, ATTRIBUTES, REVOKED_FOR_GROUP_X);

        assertThat(view(retrieveThing, context(ALICE, GROUP_X)).getSignal()).isSameAs(retrieveThing);
        assertThat(view(twinCommand, context(ALICE, GROUP_X)).getSignal()).isSameAs(twinCommand);
    }

    @Test
    public void messageWithUnchangedExtraKeepsItsAdaptable() {
        final SendThingMessage<?> message = sendThingMessage(REVOKED_FOR_GROUP_X);
        final Adaptable adaptable = ProtocolFactory.setExtra(PROTOCOL_ADAPTER.toAdaptable(message,
                        ProtocolAdapter.determineChannel(message), context(ALICE, GROUP_X)),
                JsonObject.of("{\"attributes\":{\"pub\":\"p\"}}"));

        final ReadableSignalView view = PartialAccessSignalFilter.viewFor(message, adaptable, context(ALICE, GROUP_X))
                .orElseThrow();

        assertThat(view.getSignal()).isSameAs(message);
        assertThat(view.getAdaptable()).isSameAs(adaptable);
    }

    @Test
    public void readableEventKeepsItsHeaders() {
        final AttributeModified event = AttributeModified.of(THING_ID, JsonPointer.of("obj"),
                JsonObject.of("{\"a\":1,\"secret\":2}"), 1L, Instant.EPOCH,
                revokedHeaders("{\"subjects\":[\"test:group-x\"],\"paths\":{\"attributes/obj/secret\":[0]}}"),
                null);

        final Signal<?> readable = view(event, context(ALICE, GROUP_X)).getSignal();

        assertThat(((AttributeModified) readable).getAttributeValue()).isEqualTo(JsonObject.of("{\"a\":1}"));
        assertThat(readable.getDittoHeaders()).isEqualTo(event.getDittoHeaders());
    }

    private static Adaptable withMetadata(final Signal<?> signal, final JsonObject metadata,
            final AuthorizationContext context) {
        final Adaptable adaptable =
                PROTOCOL_ADAPTER.toAdaptable(signal, ProtocolAdapter.determineChannel(signal), context);
        return ProtocolFactory.newAdaptableBuilder(adaptable)
                .withPayload(ProtocolFactory.toPayloadBuilder(adaptable.getPayload())
                        .withMetadata(Metadata.newMetadata(metadata))
                        .build())
                .build();
    }

    private static SendThingMessage<?> sendThingMessage(final DittoHeaders headers) {
        final MessageHeaders messageHeaders = MessageHeaders.newBuilder(MessageDirection.TO, THING_ID, "hello")
                .httpStatus(HttpStatus.OK)
                .build();
        final Message<String> message = Message.<String>newBuilder(messageHeaders).payload("secret-content").build();
        return SendThingMessage.of(THING_ID, message, headers);
    }

    private static ReadableSignalView view(final Signal<?> signal, final AuthorizationContext context) {
        final Optional<ReadableSignalView> view = PartialAccessSignalFilter.viewFor(signal, context);
        assertThat(view).isPresent();
        return view.orElseThrow();
    }

    private static DittoHeaders trustedHeaders(final String key, final String value) {
        return DittoHeaders.newFromTrustedJson(JsonObject.newBuilder()
                .set(key, value)
                .set(DittoHeaderDefinition.READ_SUBJECTS.getKey(), "[\"test:alice\",\"test:carol\"]")
                .build());
    }

    private static DittoHeaders revokedHeaders(final String revokedHeader) {
        return DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(), revokedHeader)
                .readGrantedSubjects(List.of(ALICE))
                .build();
    }

    private static AttributesModified attributesModified(final DittoHeaders headers) {
        return AttributesModified.of(THING_ID, ATTRIBUTES, 1L, Instant.EPOCH, headers, null);
    }

    private static AuthorizationContext context(final AuthorizationSubject first,
            final AuthorizationSubject... further) {
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, first, further);
    }

}
