/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement.pre;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.junit.jupiter.api.Test;

import com.typesafe.config.ConfigFactory;

public final class ThingCreationRestrictionPreEnforcerTest {

    private static ThingCreationRestrictionPreEnforcer load(final String basename) {
        var config = ConfigFactory.load(basename);
        final ActorSystem actorSystem = ActorSystem.create(ThingCreationRestrictionPreEnforcerTest.class.getSimpleName(),
                config);
        return new ThingCreationRestrictionPreEnforcer(actorSystem, config);
    }

    private static DittoHeaders identity(final String... subjects) {
        var authCtx = AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                Arrays.stream(subjects).map(AuthorizationSubject::newInstance).toList()
        );

        return DittoHeaders.of(Map.of(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey(), authCtx.toJsonString()));
    }

    @Test
    public void testRestrictedThingDefinition() {
        var enforcer = load("entity-creation/restricted-thing-definition");

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "org.eclipse.ditto.allowed",
                ThingsModelFactory.newDefinition("https://eclipse-ditto.github.io/ditto-examples/wot/models/foo.tm.jsonld"),
                identity("some:dully"),
                false
        );

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "org.eclipse.ditto.nope",
                ThingsModelFactory.newDefinition("https://eclipse-ditto.github.io/ditto-examples/wot/models/foo.tm.jsonld"),
                identity("some:creator"),
                false
        );

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "org.eclipse.ditto.allowed",
                ThingsModelFactory.newDefinition("https://eclipse-ditto.github.io/ditto-examples/wot/models/foo.tm.jsonld"),
                identity("some:creator"),
                true
        );

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "org.eclipse.ditto.allowed",
                ThingsModelFactory.newDefinition("https://some.other.domain/thing-definition.json"),
                identity("some:creator"),
                false
        );

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "org.eclipse.ditto.allowed",
                null, // shall be allowed as explicitly configured to allow "null" definitions
                identity("some:creator"),
                true
        );
    }

    @Test
    public void testRestrictedThingDefinitionRequiringDefinition() {
        var enforcer = load("entity-creation/restricted-thing-definition");

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "some.dimmable.lamp.namespace",
                ThingsModelFactory.newDefinition("https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp.tm.jsonld"),
                identity("another:dully"),
                false
        );

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "some.dimmable.lamp.namespace",
                null, // creation without definition is not allowed as no "nulL" was configured
                identity("another:creator"),
                false
        );

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "some.dimmable.lamp.namespace",
                ThingsModelFactory.newDefinition("https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp.tm.jsonld"),
                identity("another:creator"),
                true
        );

        testCanCreate(enforcer,
                EntityType.of("thing"),
                "some.dimmable.lamp.namespace",
                ThingsModelFactory.newDefinition("https://some.other.domain/thing-definition.json"),
                identity("another:creator"),
                false
        );
    }

    private void testCanCreate(
            final ThingCreationRestrictionPreEnforcer enforcer,
            final EntityType type,
            final String namespace,
            @Nullable final ThingDefinition thingDefinition,
            final DittoHeaders headers,
            boolean expectedOutcome) {

        assertEquals(expectedOutcome, enforcer.canCreate(new ThingCreationRestrictionPreEnforcer.ThingContext(
                type.toString(),
                namespace,
                thingDefinition,
                headers
        )));

    }
}