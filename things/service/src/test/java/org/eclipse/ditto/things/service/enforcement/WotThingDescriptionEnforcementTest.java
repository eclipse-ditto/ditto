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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveWotThingDescriptionResponse;
import org.junit.Test;

/**
 * Tests for {@link WotThingDescriptionEnforcement}.
 *
 * @since 3.9.0
 */
public final class WotThingDescriptionEnforcementTest {

    private static final String SUBJECT_ID = "test:subject";
    private static final AuthorizationContext AUTH_CONTEXT = AuthorizationModelFactory.newAuthContext(
            DittoAuthorizationContextType.UNSPECIFIED,
            AuthorizationSubject.newInstance(SUBJECT_ID));
    private static final PolicyId POLICY_ID = PolicyId.of("test:policy");

    // A thing-level TD with 2 properties, 1 action, 1 event, and sub-model links
    private static final JsonObject THING_LEVEL_TD = JsonObject.of("""
            {
              "id": "urn:test.ns:my-thing",
              "title": "My Thing",
              "@context": "https://www.w3.org/2022/wot/td/v1.1",
              "properties": {
                "temperature": {
                  "type": "number",
                  "forms": [
                    {"op": "readproperty", "href": "attributes/temperature"},
                    {"op": "writeproperty", "href": "attributes/temperature"}
                  ]
                },
                "location": {
                  "type": "string",
                  "forms": [
                    {"op": "readproperty", "href": "attributes/location"},
                    {"op": "writeproperty", "href": "attributes/location"}
                  ]
                }
              },
              "actions": {
                "reset": {
                  "forms": [{"op": "invokeaction", "href": "inbox/messages/reset"}]
                }
              },
              "events": {
                "overheated": {
                  "forms": [{"op": "subscribeevent", "href": "outbox/messages/overheated"}]
                }
              },
              "links": [
                {"rel": "type", "href": "https://example.com/model.tm.jsonld", "type": "application/tm+json"},
                {"rel": "item", "href": "features/lamp", "type": "application/td+json"},
                {"rel": "item", "href": "features/sensor", "type": "application/td+json"}
              ],
              "forms": [
                {"op": "readallproperties", "href": "attributes"},
                {"op": "writeallproperties", "href": "attributes"}
              ]
            }
            """);

    // A feature-level TD
    private static final JsonObject FEATURE_LEVEL_TD = JsonObject.of("""
            {
              "id": "urn:test.ns:my-thing/features/lamp",
              "title": "Lamp Feature",
              "@context": "https://www.w3.org/2022/wot/td/v1.1",
              "properties": {
                "on": {
                  "type": "boolean",
                  "forms": [
                    {"op": "readproperty", "href": "properties/on"},
                    {"op": "writeproperty", "href": "properties/on"}
                  ]
                },
                "brightness": {
                  "type": "integer",
                  "forms": [
                    {"op": "readproperty", "href": "properties/brightness"},
                    {"op": "writeproperty", "href": "properties/brightness"}
                  ]
                }
              },
              "actions": {
                "toggle": {
                  "forms": [{"op": "invokeaction", "href": "inbox/messages/toggle"}]
                }
              },
              "events": {
                "stateChanged": {
                  "forms": [{"op": "subscribeevent", "href": "outbox/messages/stateChanged"}]
                }
              }
            }
            """);

    @Test
    public void extractFeatureIdFromThingLevelTd() {
        assertThat(WotThingDescriptionEnforcement.extractFeatureIdFromTd(THING_LEVEL_TD)).isNull();
    }

    @Test
    public void extractFeatureIdFromFeatureLevelTd() {
        assertThat(WotThingDescriptionEnforcement.extractFeatureIdFromTd(FEATURE_LEVEL_TD)).isEqualTo("lamp");
    }

    @Test
    public void fullAccessRetainsEntireTd() {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/", Permission.READ, Permission.WRITE),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        assertThat(filtered.getValue("properties")).isPresent();
        final JsonObject props = filtered.getValue("properties").map(JsonValue::asObject).orElseThrow();
        assertThat(props.contains("temperature")).isTrue();
        assertThat(props.contains("location")).isTrue();
        assertThat(filtered.getValue("actions")).isPresent();
        assertThat(filtered.getValue("events")).isPresent();
        assertThat(filtered.getValue("forms")).isPresent();
    }

    @Test
    public void partialReadRemovesUnreadableProperties() {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/attributes/temperature", Permission.READ),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        final JsonObject props = filtered.getValue("properties").map(JsonValue::asObject).orElseThrow();
        assertThat(props.contains("temperature")).isTrue();
        assertThat(props.contains("location")).isFalse();
    }

    @Test
    public void readOnlyWhenNoWritePermission() {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/attributes/temperature", Permission.READ),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        final JsonObject tempProp = filtered.getValue("properties")
                .map(JsonValue::asObject).orElseThrow()
                .getValue("temperature").map(JsonValue::asObject).orElseThrow();

        assertThat(tempProp.getValue("readOnly")).contains(JsonValue.of(true));

        // writeproperty forms should be removed
        tempProp.getValue("forms").map(JsonValue::asArray).orElseThrow().forEach(form -> {
            final String op = form.asObject().getValue("op").map(JsonValue::asString).orElse("");
            assertThat(op).isNotEqualTo("writeproperty");
        });
    }

    @Test
    public void readAndWritePermissionKeepsPropertyFullyAccessible() {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/attributes/temperature", Permission.READ, Permission.WRITE),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        final JsonObject tempProp = filtered.getValue("properties")
                .map(JsonValue::asObject).orElseThrow()
                .getValue("temperature").map(JsonValue::asObject).orElseThrow();

        // readOnly should NOT be set
        assertThat(tempProp.getValue("readOnly").map(JsonValue::asBoolean).orElse(false)).isFalse();
    }

    @Test
    public void noMessageWriteRemovesActions() {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/", Permission.READ, Permission.WRITE),
                grant("message", "/outbox/messages/overheated", Permission.READ)
                // no WRITE on inbox/messages → actions removed
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        assertThat(filtered.contains("actions")).isFalse();
    }

    @Test
    public void noMessageReadRemovesEvents() {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/", Permission.READ, Permission.WRITE),
                grant("message", "/inbox/messages/reset", Permission.WRITE)
                // no READ on outbox/messages → events removed
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        assertThat(filtered.contains("events")).isFalse();
    }

    @Test
    public void submodelLinksFilteredByFeatureAccess() {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/", Permission.READ, Permission.WRITE),
                // only grant access to feature "lamp", not "sensor"
                revoke("thing", "/features/sensor", Permission.READ),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        final var links = filtered.getValue("links").map(JsonValue::asArray).orElseThrow();
        // "type" link should remain, "lamp" item link should remain, "sensor" should be removed
        final long itemLinkCount = links.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .filter(link -> link.getValue("rel").map(JsonValue::asString).orElse("").equals("item"))
                .count();
        assertThat(itemLinkCount).isEqualTo(1);

        // verify the remaining item link points to lamp
        final boolean hasLampLink = links.stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .filter(link -> link.getValue("rel").map(JsonValue::asString).orElse("").equals("item"))
                .anyMatch(link -> link.getValue("href").map(JsonValue::asString).orElse("").equals("features/lamp"));
        assertThat(hasLampLink).isTrue();
    }

    @Test
    public void featureLevelTdUsesCorrectResourcePaths() {
        // grant READ+WRITE on feature lamp property "on" but only READ on "brightness"
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/features/lamp/properties/on", Permission.READ, Permission.WRITE),
                grant("thing", "/features/lamp/properties/brightness", Permission.READ),
                grant("message", "/features/lamp/inbox/messages/toggle", Permission.WRITE),
                grant("message", "/features/lamp/outbox/messages/stateChanged", Permission.READ)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                FEATURE_LEVEL_TD, enforcer, AUTH_CONTEXT, "lamp");

        // both properties should be present
        final JsonObject props = filtered.getValue("properties").map(JsonValue::asObject).orElseThrow();
        assertThat(props.contains("on")).isTrue();
        assertThat(props.contains("brightness")).isTrue();

        // "on" should not be readOnly
        assertThat(props.getValue("on").map(JsonValue::asObject).orElseThrow()
                .getValue("readOnly").map(JsonValue::asBoolean).orElse(false)).isFalse();

        // "brightness" should be readOnly
        assertThat(props.getValue("brightness").map(JsonValue::asObject).orElseThrow()
                .getValue("readOnly")).contains(JsonValue.of(true));

        // action and event should remain
        assertThat(filtered.contains("actions")).isTrue();
        assertThat(filtered.contains("events")).isTrue();
    }

    @Test
    public void rootFormsRemovedWhenNoReadableProperties() {
        // no READ access to any attribute
        final Enforcer enforcer = createEnforcer(
                grant("message", "/", Permission.READ, Permission.WRITE)
                // no thing permissions
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        // no properties should remain
        assertThat(filtered.contains("properties")).isFalse();
        // root forms should be removed
        assertThat(filtered.contains("forms")).isFalse();
    }

    @Test
    public void writeRootFormRemovedWhenNoWritableProperties() {
        // READ on all attributes but no WRITE
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/attributes", Permission.READ),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        // properties should remain but be readOnly
        assertThat(filtered.getValue("properties")).isPresent();

        // root forms: readallproperties should remain, writeallproperties should be removed
        final var forms = filtered.getValue("forms").map(JsonValue::asArray);
        assertThat(forms).isPresent();
        final boolean hasReadAll = forms.get().stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .anyMatch(f -> f.getValue("op").map(JsonValue::asString).orElse("").equals("readallproperties"));
        final boolean hasWriteAll = forms.get().stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .anyMatch(f -> f.getValue("op").map(JsonValue::asString).orElse("").equals("writeallproperties"));
        assertThat(hasReadAll).isTrue();
        assertThat(hasWriteAll).isFalse();
    }

    @Test
    public void emptyTdReturnedWhenNoPermissions() {
        // empty policy → no permissions at all
        final Enforcer enforcer = createEnforcer();

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                THING_LEVEL_TD, enforcer, AUTH_CONTEXT, null);

        // metadata should remain
        assertThat(filtered.getValue("id")).isPresent();
        assertThat(filtered.getValue("title")).isPresent();
        assertThat(filtered.getValue("@context")).isPresent();

        // affordances should be removed
        assertThat(filtered.contains("properties")).isFalse();
        assertThat(filtered.contains("actions")).isFalse();
        assertThat(filtered.contains("events")).isFalse();
        assertThat(filtered.contains("forms")).isFalse();
    }

    @Test
    public void categoryBasedPropertyHrefHandledCorrectly() {
        // TD with a property that has a category prefix in the href
        final JsonObject tdWithCategory = JsonObject.of("""
                {
                  "id": "urn:test.ns:my-thing",
                  "properties": {
                    "temperature": {
                      "type": "number",
                      "forms": [
                        {"op": "readproperty", "href": "attributes/environment/temperature"}
                      ]
                    }
                  }
                }
                """);

        final Enforcer enforcer = createEnforcer(
                grant("thing", "/attributes/environment/temperature", Permission.READ)
        );

        final JsonObject filtered = WotThingDescriptionEnforcement.filterTdJson(
                tdWithCategory, enforcer, AUTH_CONTEXT, null);

        assertThat(filtered.getValue("properties").map(JsonValue::asObject).orElseThrow()
                .contains("temperature")).isTrue();
    }

    // --- response-level filtering tests ---

    @Test
    public void filterThingDescriptionResponseRemovesUnauthorizedProperties() throws Exception {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/attributes/temperature", Permission.READ),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );
        final RetrieveWotThingDescriptionResponse response = buildTdResponse(THING_LEVEL_TD);

        final RetrieveWotThingDescriptionResponse filtered =
                WotThingDescriptionEnforcement.filterThingDescription(response, enforcer)
                        .toCompletableFuture().get();

        final JsonObject filteredTd = filtered.getEntity(filtered.getImplementedSchemaVersion()).asObject();

        // temperature should remain (user has READ), location should be removed
        final JsonObject props = filteredTd.getValue("properties").map(JsonValue::asObject).orElseThrow();
        assertThat(props.contains("temperature")).isTrue();
        assertThat(props.contains("location")).isFalse();

        // temperature should be readOnly (user has no WRITE)
        assertThat(props.getValue("temperature").map(JsonValue::asObject).orElseThrow()
                .getValue("readOnly")).contains(JsonValue.of(true));
    }

    @Test
    public void filterThingDescriptionResponsePreservesThingId() throws Exception {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/", Permission.READ, Permission.WRITE),
                grant("message", "/", Permission.READ, Permission.WRITE)
        );
        final RetrieveWotThingDescriptionResponse response = buildTdResponse(THING_LEVEL_TD);

        final RetrieveWotThingDescriptionResponse filtered =
                WotThingDescriptionEnforcement.filterThingDescription(response, enforcer)
                        .toCompletableFuture().get();

        assertThat((Object) filtered.getEntityId()).isEqualTo(response.getEntityId());
        assertThat((Object) filtered.getDittoHeaders()).isEqualTo(response.getDittoHeaders());
    }

    @Test
    public void filterThingDescriptionResponseStripsAllAffordancesWithNoPermissions() throws Exception {
        final Enforcer enforcer = createEnforcer();
        final RetrieveWotThingDescriptionResponse response = buildTdResponse(THING_LEVEL_TD);

        final RetrieveWotThingDescriptionResponse filtered =
                WotThingDescriptionEnforcement.filterThingDescription(response, enforcer)
                        .toCompletableFuture().get();

        final JsonObject filteredTd = filtered.getEntity(filtered.getImplementedSchemaVersion()).asObject();
        assertThat(filteredTd.contains("properties")).isFalse();
        assertThat(filteredTd.contains("actions")).isFalse();
        assertThat(filteredTd.contains("events")).isFalse();
        // metadata must survive
        assertThat(filteredTd.getValue("id")).isPresent();
    }

    @Test
    public void filterFeatureLevelThingDescriptionResponse() throws Exception {
        final Enforcer enforcer = createEnforcer(
                grant("thing", "/features/lamp/properties/on", Permission.READ, Permission.WRITE),
                grant("message", "/features/lamp/inbox/messages/toggle", Permission.WRITE),
                grant("message", "/features/lamp/outbox/messages/stateChanged", Permission.READ)
                // no access to "brightness"
        );
        final RetrieveWotThingDescriptionResponse response = buildTdResponse(FEATURE_LEVEL_TD);

        final RetrieveWotThingDescriptionResponse filtered =
                WotThingDescriptionEnforcement.filterThingDescription(response, enforcer)
                        .toCompletableFuture().get();

        final JsonObject filteredTd = filtered.getEntity(filtered.getImplementedSchemaVersion()).asObject();

        // "on" should be present, "brightness" removed (no READ)
        final JsonObject props = filteredTd.getValue("properties").map(JsonValue::asObject).orElseThrow();
        assertThat(props.contains("on")).isTrue();
        assertThat(props.contains("brightness")).isFalse();

        // action and event should remain
        assertThat(filteredTd.contains("actions")).isTrue();
        assertThat(filteredTd.contains("events")).isTrue();
    }

    private static RetrieveWotThingDescriptionResponse buildTdResponse(final JsonObject td) {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .authorizationContext(AUTH_CONTEXT)
                .build();
        return RetrieveWotThingDescriptionResponse.of(
                ThingId.of("test.ns", "my-thing"), td, headers);
    }

    // --- helper methods ---

    private static Enforcer createEnforcer(final ResourceGrant... grants) {
        var policyBuilder = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L);

        int entryIndex = 0;
        for (final ResourceGrant grant : grants) {
            final String entryLabel = "entry-" + entryIndex++;
            policyBuilder.set(PoliciesModelFactory.newPolicyEntry(entryLabel,
                    Collections.singletonList(Subject.newInstance(SubjectId.newInstance(SUBJECT_ID))),
                    Collections.singletonList(PoliciesModelFactory.newResource(
                            grant.resourceType, grant.path,
                            EffectedPermissions.newInstance(
                                    grant.grantedPermissions,
                                    grant.revokedPermissions)))));
        }

        final Policy policy = policyBuilder.build();
        return PolicyEnforcer.of(policy).getEnforcer();
    }

    private static ResourceGrant grant(final String resourceType, final String path, final String... permissions) {
        return new ResourceGrant(resourceType, path,
                permissions.length > 0 ? Permissions.newInstance(permissions[0],
                java.util.Arrays.copyOfRange(permissions, 1, permissions.length)) : Permissions.none(),
                Permissions.none());
    }

    private static ResourceGrant revoke(final String resourceType, final String path, final String... permissions) {
        return new ResourceGrant(resourceType, path,
                Permissions.none(),
                permissions.length > 0 ? Permissions.newInstance(permissions[0],
                java.util.Arrays.copyOfRange(permissions, 1, permissions.length)) : Permissions.none());
    }

    private record ResourceGrant(String resourceType, String path,
                                 Permissions grantedPermissions, Permissions revokedPermissions) {}
}
