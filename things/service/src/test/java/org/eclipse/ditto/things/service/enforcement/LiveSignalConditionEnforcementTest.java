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
package org.eclipse.ditto.things.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.things.service.enforcement.TestSetup.THING_ID;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageBuilder;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.junit.Test;

/**
 * Tests that {@link LiveSignalEnforcement} enforces READ permission on the fields referenced in the {@code condition}
 * header of live signals, as the condition is evaluated against the full thing before publishing the live signal.
 */
public final class LiveSignalConditionEnforcementTest {

    private static final String READABLE_CONDITION = "eq(attributes/pub,\"p\")";
    private static final String UNREADABLE_CONDITION = "eq(attributes/secret,\"A7\")";

    private static final String MESSENGER = "messenger";
    private static final String WRITER = "writer";
    private static final String NOBODY = "nobody";

    private final LiveSignalEnforcement underTest = new LiveSignalEnforcement(false);

    private final PolicyEnforcer policyEnforcer =
            PolicyEnforcer.of(PoliciesModelFactory.newPolicyBuilder(PolicyId.of("policy:id"))
                    .setRevision(1L)
                    .forLabel("messenger")
                    .setSubject(SubjectIssuer.GOOGLE, MESSENGER)
                    .setGrantedPermissions(PoliciesResourceType.messageResource(JsonPointer.empty()),
                            Permissions.newInstance(Permission.WRITE))
                    .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.of("/attributes/pub")),
                            Permissions.newInstance(Permission.READ))
                    .forLabel("writer")
                    .setSubject(SubjectIssuer.GOOGLE, WRITER)
                    .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                            Permissions.newInstance(Permission.READ, Permission.WRITE))
                    .setRevokedPermissions(PoliciesResourceType.thingResource(JsonPointer.of("/attributes/secret")),
                            Permissions.newInstance(Permission.READ))
                    .build());

    @Test
    public void rejectMessageWithConditionOnUnreadableField() {
        assertThatThrownBy(() -> authorize(thingMessage(MESSENGER, UNREADABLE_CONDITION)))
                .isInstanceOf(ThingConditionFailedException.class);
    }

    @Test
    public void acceptMessageWithConditionOnReadableField() {
        assertThat(authorize(thingMessage(MESSENGER, READABLE_CONDITION))).isInstanceOf(SendThingMessage.class);
    }

    @Test
    public void rejectLiveEventWithConditionOnUnreadableField() {
        assertThatThrownBy(() -> authorize(liveEvent(WRITER, UNREADABLE_CONDITION)))
                .isInstanceOf(ThingConditionFailedException.class);
    }

    @Test
    public void acceptLiveEventWithConditionOnReadableField() {
        assertThat(authorize(liveEvent(WRITER, READABLE_CONDITION))).isInstanceOf(AttributeModified.class);
    }

    @Test
    public void rejectLiveCreateThingWithConditionOnUnreadableField() {
        final CreateThing createThing = CreateThing.of(ThingsModelFactory.newThingBuilder().setId(THING_ID).build(),
                null, liveHeaders(WRITER, UNREADABLE_CONDITION));

        assertThatThrownBy(() -> authorize(createThing)).isInstanceOf(ThingConditionFailedException.class);
    }

    @Test
    public void rejectClaimMessageWithCondition() {
        assertThatThrownBy(() -> authorize(claimMessage(NOBODY, READABLE_CONDITION)))
                .isInstanceOf(DittoHeaderInvalidException.class)
                .hasMessageContaining("condition");
    }

    @Test
    public void acceptClaimMessageWithoutCondition() {
        assertThat(authorize(claimMessage(NOBODY, null))).isInstanceOf(SendClaimMessage.class);
    }

    private Signal<?> authorize(final Signal<?> signal) {
        return underTest.authorizeSignal(signal, policyEnforcer).toCompletableFuture().join();
    }

    private static SendThingMessage<String> thingMessage(final String subjectId, final String condition) {
        return SendThingMessage.of(THING_ID, message("ping"), liveHeaders(subjectId, condition));
    }

    private static SendClaimMessage<String> claimMessage(final String subjectId, final String condition) {
        return SendClaimMessage.of(THING_ID, message("claim"), liveHeaders(subjectId, condition));
    }

    private static AttributeModified liveEvent(final String subjectId, final String condition) {
        return AttributeModified.of(THING_ID, JsonPointer.of("pub"), JsonValue.of("q"), 1L, null,
                liveHeaders(subjectId, condition), null);
    }

    private static Message<String> message(final String subject) {
        return Message.<String>newBuilder(MessageBuilder.newHeadersBuilder(MessageDirection.TO, THING_ID, subject)
                        .contentType("text/plain")
                        .build())
                .payload("hello")
                .build();
    }

    private static DittoHeaders liveHeaders(final String subjectId, final String condition) {
        final var builder = DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(SubjectIssuer.GOOGLE + ":" + subjectId)))
                .channel("live")
                .randomCorrelationId();
        if (null != condition) {
            builder.condition(condition);
        }
        return builder.build();
    }

}
