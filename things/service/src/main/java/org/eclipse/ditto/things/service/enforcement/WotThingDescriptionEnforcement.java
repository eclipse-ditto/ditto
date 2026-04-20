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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveWotThingDescriptionResponse;
import org.eclipse.ditto.wot.model.FormElement;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.ThingSkeleton;

/**
 * Utility for filtering a WoT Thing Description based on the requesting user's policy permissions.
 * <p>
 * Properties the user cannot READ are removed. Properties the user cannot WRITE are marked {@code readOnly}.
 * Actions the user cannot invoke (WRITE on message resource) are removed.
 * Events the user cannot subscribe to (READ on message resource) are removed.
 * Sub-model links to inaccessible features are removed.
 *
 * @since 3.9.0
 */
@Immutable
final class WotThingDescriptionEnforcement {

    private static final Permissions READ_PERMISSION = Permissions.newInstance(Permission.READ);
    private static final Permissions WRITE_PERMISSION = Permissions.newInstance(Permission.WRITE);

    private static final Pattern FEATURE_ID_FROM_TD_ID_PATTERN =
            Pattern.compile(".*/features/([^/]+)$");
    private static final Pattern FEATURE_ID_FROM_LINK_HREF_PATTERN =
            Pattern.compile("^features/([^/]+)$");

    private static final String OP_FIELD = FormElement.JsonFields.OP.getPointer().getRoot()
            .map(Object::toString).orElse("op");
    private static final String HREF_FIELD = FormElement.JsonFields.HREF.getPointer().getRoot()
            .map(Object::toString).orElse("href");
    private static final String READ_ONLY_FIELD = SingleDataSchema.DataSchemaJsonFields.READ_ONLY.getPointer()
            .getRoot().map(Object::toString).orElse("readOnly");

    private WotThingDescriptionEnforcement() {
        throw new AssertionError();
    }

    /**
     * Filters the TD in the given response based on the user's permissions determined by the enforcer.
     *
     * @param response the WoT TD response to filter.
     * @param enforcer the policy enforcer to check permissions with.
     * @return a completion stage with the filtered response.
     */
    static CompletionStage<RetrieveWotThingDescriptionResponse> filterThingDescription(
            final RetrieveWotThingDescriptionResponse response,
            final Enforcer enforcer) {

        final JsonObject tdJson = response.getEntity(response.getImplementedSchemaVersion()).asObject();
        final AuthorizationContext authContext = response.getDittoHeaders().getAuthorizationContext();
        final String featureId = extractFeatureIdFromTd(tdJson);

        final JsonObject filteredTd = filterTdJson(tdJson, enforcer, authContext, featureId);
        return CompletableFuture.completedFuture(response.setEntity(filteredTd));
    }

    /**
     * Filters the TD JSON object based on permissions.
     *
     * @param tdJson the full TD JSON.
     * @param enforcer the policy enforcer.
     * @param authContext the user's authorization context.
     * @param featureId the feature ID if this is a feature-level TD, or {@code null} for thing-level.
     * @return the filtered TD JSON.
     */
    static JsonObject filterTdJson(final JsonObject tdJson,
            final Enforcer enforcer,
            final AuthorizationContext authContext,
            @Nullable final String featureId) {

        final JsonObjectBuilder builder = tdJson.toBuilder();

        final boolean hasWritableProperties = filterProperties(tdJson, builder, enforcer, authContext, featureId);
        final boolean hasReadableProperties = builder.build()
                .getValue(ThingSkeleton.JsonFields.PROPERTIES)
                .filter(obj -> !obj.isEmpty())
                .isPresent();

        filterActions(tdJson, builder, enforcer, authContext, featureId);
        filterEvents(tdJson, builder, enforcer, authContext, featureId);
        filterSubmodelLinks(tdJson, builder, enforcer, authContext);
        filterRootForms(tdJson, builder, hasReadableProperties, hasWritableProperties);

        return builder.build();
    }

    /**
     * Filters properties in the TD based on READ/WRITE permissions.
     *
     * @return {@code true} if at least one property remains writable after filtering.
     */
    private static boolean filterProperties(final JsonObject tdJson,
            final JsonObjectBuilder tdBuilder,
            final Enforcer enforcer,
            final AuthorizationContext authContext,
            @Nullable final String featureId) {

        return tdJson.getValue(ThingSkeleton.JsonFields.PROPERTIES)
                .filter(props -> !props.isEmpty())
                .map(propertiesJson -> {
                    final JsonObjectBuilder propsBuilder = JsonObject.newBuilder();
                    boolean anyWritable = false;

                    for (final JsonField field : propertiesJson) {
                        final String propertyName = field.getKeyName();
                        final JsonObject propertyJson = field.getValue().asObject();

                        final String resourcePath = extractResourcePathFromProperty(propertyJson, propertyName,
                                featureId);
                        final ResourceKey thingResourceKey =
                                PoliciesResourceType.thingResource(JsonPointer.of(resourcePath));

                        final boolean canRead = enforcer.hasUnrestrictedPermissions(thingResourceKey, authContext,
                                READ_PERMISSION);
                        if (!canRead) {
                            continue; // skip this property entirely
                        }

                        final boolean canWrite = enforcer.hasUnrestrictedPermissions(thingResourceKey, authContext,
                                WRITE_PERMISSION);
                        if (canWrite) {
                            anyWritable = true;
                            propsBuilder.set(propertyName, propertyJson);
                        } else {
                            // mark as readOnly and remove WRITEPROPERTY forms
                            propsBuilder.set(propertyName, makePropertyReadOnly(propertyJson));
                        }
                    }

                    final JsonObject filteredProps = propsBuilder.build();
                    if (filteredProps.isEmpty()) {
                        tdBuilder.remove(ThingSkeleton.JsonFields.PROPERTIES);
                    } else {
                        tdBuilder.set(ThingSkeleton.JsonFields.PROPERTIES, filteredProps);
                    }
                    return anyWritable;
                })
                .orElse(false);
    }

    /**
     * Filters actions in the TD based on WRITE permission on message resources.
     */
    private static void filterActions(final JsonObject tdJson,
            final JsonObjectBuilder tdBuilder,
            final Enforcer enforcer,
            final AuthorizationContext authContext,
            @Nullable final String featureId) {

        tdJson.getValue(ThingSkeleton.JsonFields.ACTIONS)
                .filter(actions -> !actions.isEmpty())
                .ifPresent(actionsJson -> {
                    final JsonObjectBuilder actionsBuilder = JsonObject.newBuilder();

                    for (final JsonField field : actionsJson) {
                        final String actionName = field.getKeyName();
                        final String messagePath = buildMessagePath("inbox/messages/" + actionName, featureId);
                        final ResourceKey messageResourceKey =
                                PoliciesResourceType.messageResource(JsonPointer.of(messagePath));

                        if (enforcer.hasUnrestrictedPermissions(messageResourceKey, authContext, WRITE_PERMISSION)) {
                            actionsBuilder.set(actionName, field.getValue());
                        }
                    }

                    final JsonObject filteredActions = actionsBuilder.build();
                    if (filteredActions.isEmpty()) {
                        tdBuilder.remove(ThingSkeleton.JsonFields.ACTIONS);
                    } else {
                        tdBuilder.set(ThingSkeleton.JsonFields.ACTIONS, filteredActions);
                    }
                });
    }

    /**
     * Filters events in the TD based on READ permission on message resources.
     */
    private static void filterEvents(final JsonObject tdJson,
            final JsonObjectBuilder tdBuilder,
            final Enforcer enforcer,
            final AuthorizationContext authContext,
            @Nullable final String featureId) {

        tdJson.getValue(ThingSkeleton.JsonFields.EVENTS)
                .filter(events -> !events.isEmpty())
                .ifPresent(eventsJson -> {
                    final JsonObjectBuilder eventsBuilder = JsonObject.newBuilder();

                    for (final JsonField field : eventsJson) {
                        final String eventName = field.getKeyName();
                        final String messagePath = buildMessagePath("outbox/messages/" + eventName, featureId);
                        final ResourceKey messageResourceKey =
                                PoliciesResourceType.messageResource(JsonPointer.of(messagePath));

                        if (enforcer.hasUnrestrictedPermissions(messageResourceKey, authContext, READ_PERMISSION)) {
                            eventsBuilder.set(eventName, field.getValue());
                        }
                    }

                    final JsonObject filteredEvents = eventsBuilder.build();
                    if (filteredEvents.isEmpty()) {
                        tdBuilder.remove(ThingSkeleton.JsonFields.EVENTS);
                    } else {
                        tdBuilder.set(ThingSkeleton.JsonFields.EVENTS, filteredEvents);
                    }
                });
    }

    /**
     * Filters sub-model links (rel="item") in thing-level TDs based on feature access.
     */
    private static void filterSubmodelLinks(final JsonObject tdJson,
            final JsonObjectBuilder tdBuilder,
            final Enforcer enforcer,
            final AuthorizationContext authContext) {

        tdJson.getValue(ThingSkeleton.JsonFields.LINKS)
                .filter(links -> !links.isEmpty())
                .ifPresent(linksArray -> {
                    final List<JsonValue> filteredLinks = new ArrayList<>();

                    for (final JsonValue linkValue : linksArray) {
                        if (!linkValue.isObject()) {
                            filteredLinks.add(linkValue);
                            continue;
                        }
                        final JsonObject link = linkValue.asObject();
                        final Optional<String> rel = link.getValue("rel")
                                .filter(JsonValue::isString)
                                .map(JsonValue::asString);
                        final Optional<String> href = link.getValue(HREF_FIELD)
                                .filter(JsonValue::isString)
                                .map(JsonValue::asString);

                        if (rel.filter("item"::equals).isPresent() && href.isPresent()) {
                            final Matcher matcher = FEATURE_ID_FROM_LINK_HREF_PATTERN.matcher(href.get());
                            if (matcher.matches()) {
                                final String linkedFeatureId = matcher.group(1);
                                final ResourceKey featureResourceKey = PoliciesResourceType.thingResource(
                                        JsonPointer.of("/features/" + linkedFeatureId));
                                if (!enforcer.hasPartialPermissions(featureResourceKey, authContext,
                                        READ_PERMISSION)) {
                                    continue; // skip this link
                                }
                            }
                        }
                        filteredLinks.add(linkValue);
                    }

                    final JsonArray filteredLinksArray = filteredLinks.stream()
                            .collect(JsonCollectors.valuesToArray());
                    tdBuilder.set(ThingSkeleton.JsonFields.LINKS, filteredLinksArray);
                });
    }

    /**
     * Filters root-level forms based on whether readable/writable properties remain.
     */
    private static void filterRootForms(final JsonObject tdJson,
            final JsonObjectBuilder tdBuilder,
            final boolean hasReadableProperties,
            final boolean hasWritableProperties) {

        tdJson.getValue(ThingSkeleton.JsonFields.FORMS)
                .filter(forms -> !forms.isEmpty())
                .ifPresent(formsArray -> {
                    final List<JsonValue> filteredForms = new ArrayList<>();

                    for (final JsonValue formValue : formsArray) {
                        if (!formValue.isObject()) {
                            filteredForms.add(formValue);
                            continue;
                        }
                        final JsonObject form = formValue.asObject();
                        final Optional<String> op = extractOp(form);

                        if (op.isPresent()) {
                            final String opName = op.get();
                            if (isReadOp(opName) && !hasReadableProperties) {
                                continue; // remove read forms when no readable properties
                            }
                            if (isWriteOp(opName) && !hasWritableProperties) {
                                continue; // remove write forms when no writable properties
                            }
                        }
                        filteredForms.add(formValue);
                    }

                    if (filteredForms.isEmpty()) {
                        tdBuilder.remove(ThingSkeleton.JsonFields.FORMS);
                    } else {
                        tdBuilder.set(ThingSkeleton.JsonFields.FORMS,
                                filteredForms.stream().collect(JsonCollectors.valuesToArray()));
                    }
                });
    }

    /**
     * Extracts the feature ID from the TD's {@code "id"} field.
     *
     * @return the feature ID, or {@code null} if this is a thing-level TD.
     */
    @Nullable
    static String extractFeatureIdFromTd(final JsonObject tdJson) {
        return tdJson.getValue(ThingSkeleton.JsonFields.ID)
                .map(FEATURE_ID_FROM_TD_ID_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(m -> m.group(1))
                .orElse(null);
    }

    /**
     * Extracts the thing resource path for a property from its first form's {@code href},
     * falling back to a constructed path from the property name.
     */
    private static String extractResourcePathFromProperty(final JsonObject propertyJson,
            final String propertyName,
            @Nullable final String featureId) {

        // try to extract from the first form's href
        final Optional<String> hrefFromForm = propertyJson.getValue("forms")
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .filter(arr -> !arr.isEmpty())
                .flatMap(arr -> arr.get(0))
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .flatMap(form -> form.getValue(HREF_FIELD))
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(WotThingDescriptionEnforcement::stripUriVariables);

        if (hrefFromForm.isPresent()) {
            if (featureId != null) {
                return "/features/" + featureId + "/" + hrefFromForm.get();
            } else {
                return "/" + hrefFromForm.get();
            }
        }

        // fallback: construct from property name
        if (featureId != null) {
            return "/features/" + featureId + "/properties/" + propertyName;
        } else {
            return "/attributes/" + propertyName;
        }
    }

    /**
     * Strips URI template variable suffixes like {@code {?channel,timeout}} from an href.
     */
    private static String stripUriVariables(final String href) {
        final int braceIndex = href.indexOf('{');
        return braceIndex >= 0 ? href.substring(0, braceIndex) : href;
    }

    /**
     * Makes a property read-only: sets {@code "readOnly": true} and removes WRITEPROPERTY form elements.
     */
    private static JsonObject makePropertyReadOnly(final JsonObject propertyJson) {
        final JsonObjectBuilder builder = propertyJson.toBuilder()
                .set(READ_ONLY_FIELD, true);

        // remove WRITEPROPERTY form elements
        propertyJson.getValue("forms")
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .ifPresent(forms -> {
                    final List<JsonValue> filteredForms = new ArrayList<>();
                    for (final JsonValue formValue : forms) {
                        if (formValue.isObject()) {
                            final Optional<String> op = extractOp(formValue.asObject());
                            if (op.filter("writeproperty"::equals).isPresent()) {
                                continue; // skip write forms
                            }
                        }
                        filteredForms.add(formValue);
                    }
                    builder.set("forms", filteredForms.stream().collect(JsonCollectors.valuesToArray()));
                });

        return builder.build();
    }

    /**
     * Extracts the {@code "op"} value from a form element. Handles both single string and array values.
     */
    private static Optional<String> extractOp(final JsonObject form) {
        return form.getValue(OP_FIELD)
                .map(opValue -> {
                    if (opValue.isString()) {
                        return opValue.asString();
                    } else if (opValue.isArray()) {
                        // for array op, return the first entry
                        return opValue.asArray().stream()
                                .filter(JsonValue::isString)
                                .map(JsonValue::asString)
                                .findFirst()
                                .orElse(null);
                    }
                    return null;
                });
    }

    private static boolean isReadOp(final String op) {
        return "readallproperties".equals(op) || "readmultipleproperties".equals(op)
                || "observeallproperties".equals(op);
    }

    private static boolean isWriteOp(final String op) {
        return "writeallproperties".equals(op) || "writemultipleproperties".equals(op);
    }

    /**
     * Builds the message resource path, prepending the feature path for feature-level TDs.
     */
    private static String buildMessagePath(final String relativePath, @Nullable final String featureId) {
        if (featureId != null) {
            return "/features/" + featureId + "/" + relativePath;
        } else {
            return "/" + relativePath;
        }
    }
}
