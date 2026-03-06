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
package org.eclipse.ditto.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Provides the {@code fn:format('{field1}#{field2}')} function implementation.
 * <p>
 * This function processes each input value (expected to be a JSON object string) individually,
 * keeping field extractions correlated within each object. This avoids the Cartesian product
 * problem that occurs when multiple placeholders each resolve independently across array elements.
 * </p>
 * <p>
 * Template syntax (Mustache-inspired, using single braces to avoid collision with Ditto's
 * {@code {{ placeholder }}} syntax):
 * <ul>
 *     <li>{@code {fieldName}} — extracts a top-level field from the JSON object</li>
 *     <li>{@code {/json/pointer}} — extracts a nested field using JSON Pointer syntax</li>
 *     <li>{@code {#arrayField}...{/arrayField}} — iterates over an array of objects, resolving
 *         the inner template against each array element</li>
 *     <li>{@code {.}} — inside a section, references the current element itself
 *         (useful for arrays of primitives)</li>
 *     <li>{@code \{} and {@code \}} — escaped braces, rendered literally</li>
 * </ul>
 * </p>
 * <p>
 * If a field resolves to a JSON array of primitives (outside a section), the function expands it
 * (Cartesian product within a single object). For arrays of objects, use sections to iterate.
 * Missing or null fields are substituted with an empty string.
 * </p>
 * @since 3.9.0
 */
@Immutable
final class PipelineFunctionFormat implements PipelineFunction {

    private static final String FUNCTION_NAME = "format";

    /**
     * Matches section opening tags: {@code {#sectionName}}.
     * Uses negative lookbehind for backslash.
     */
    private static final Pattern SECTION_OPEN_PATTERN = Pattern.compile("(?<!\\\\)\\{#([^}]+)}");

    /**
     * Matches escaped braces.
     */
    private static final Pattern ESCAPED_OPEN_BRACE = Pattern.compile("\\\\\\{");
    private static final Pattern ESCAPED_CLOSE_BRACE = Pattern.compile("\\\\}");

    private final PipelineFunctionParameterResolverFactory.SingleParameterResolver parameterResolver =
            PipelineFunctionParameterResolverFactory.forStringParameter();

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public Signature getSignature() {
        return FormatFunctionSignature.INSTANCE;
    }

    @Override
    public PipelineElement apply(final PipelineElement value, final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        final String template = parseAndResolve(paramsIncludingParentheses, expressionResolver);
        final List<TemplatePart> templateParts = parseTemplate(template);

        final List<String> allResults = new ArrayList<>();
        for (final String inputValue : value) {
            final JsonObject jsonObject;
            try {
                jsonObject = JsonObject.of(inputValue);
            } catch (final Exception e) {
                // If input is not a valid JSON object, skip it
                continue;
            }
            final List<String> formatted = formatObject(jsonObject, templateParts);
            allResults.addAll(formatted);
        }

        if (allResults.isEmpty()) {
            return value.getType() == PipelineElement.Type.UNRESOLVED
                    ? PipelineElement.unresolved()
                    : PipelineElement.resolved(Collections.singletonList(""));
        }
        return PipelineElement.resolved(allResults);
    }

    private String parseAndResolve(final String paramsIncludingParentheses,
            final ExpressionResolver expressionResolver) {

        return parameterResolver.apply(paramsIncludingParentheses, expressionResolver, this)
                .findFirst()
                .orElseThrow(
                        () -> PlaceholderFunctionSignatureInvalidException.newBuilder(paramsIncludingParentheses, this)
                                .build());
    }

    /**
     * Parses the template into a list of static text parts, field reference parts, and section parts.
     * Sections are detected first via {@code {#name}...{/name}}, then the remaining fragments are
     * parsed for field references and static text.
     */
    static List<TemplatePart> parseTemplate(final String template) {
        return parseTemplateRecursive(template);
    }

    private static List<TemplatePart> parseTemplateRecursive(final String template) {
        final List<TemplatePart> parts = new ArrayList<>();
        int pos = 0;

        while (pos < template.length()) {
            // Look for the next section opening tag
            final Matcher sectionMatcher = SECTION_OPEN_PATTERN.matcher(template);
            if (sectionMatcher.find(pos)) {
                // Parse any content before the section as flat parts
                if (sectionMatcher.start() > pos) {
                    parts.addAll(parseFlatParts(template.substring(pos, sectionMatcher.start())));
                }

                final String sectionName = sectionMatcher.group(1);
                final String closingTag = "{/" + sectionName + "}";
                final int bodyStart = sectionMatcher.end();
                final int closingIndex = findClosingTag(template, bodyStart, sectionName);
                if (closingIndex < 0) {
                    throw new IllegalArgumentException(
                            "Unclosed section tag {#" + sectionName + "} in format template");
                }
                final String sectionBody = template.substring(bodyStart, closingIndex);
                final List<TemplatePart> innerParts = parseTemplateRecursive(sectionBody);
                parts.add(new SectionPart(sectionName, innerParts));
                pos = closingIndex + closingTag.length();
            } else {
                // No more sections — parse rest as flat parts
                parts.addAll(parseFlatParts(template.substring(pos)));
                pos = template.length();
            }
        }

        return parts;
    }

    /**
     * Finds the matching closing tag for a section, handling nested sections with the same name.
     *
     * @return the index of the closing tag, or -1 if not found
     */
    private static int findClosingTag(final String template, final int fromIndex, final String sectionName) {
        final String openTag = "{#" + sectionName + "}";
        final String closeTag = "{/" + sectionName + "}";
        int depth = 1;
        int pos = fromIndex;

        while (pos < template.length() && depth > 0) {
            final int nextOpen = template.indexOf(openTag, pos);
            final int nextClose = template.indexOf(closeTag, pos);

            if (nextClose < 0) {
                return -1; // no closing tag found
            }

            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                pos = nextOpen + openTag.length();
            } else {
                depth--;
                if (depth == 0) {
                    return nextClose;
                }
                pos = nextClose + closeTag.length();
            }
        }

        return -1;
    }

    /**
     * Parses a template fragment that contains no section tags — only static text and field references.
     */
    private static List<TemplatePart> parseFlatParts(final String fragment) {
        final List<TemplatePart> parts = new ArrayList<>();
        // Match {fieldReference} but not \{...\} and not {#section} or {/section}
        // JSON pointers like {/user/name} start with / followed by at least one more path segment,
        // so they always contain at least 2 slashes. Section closing tags {/name} have exactly one
        // segment after the slash. We use negative lookahead to exclude {#...} and single-segment {/...}.
        final Pattern fieldPattern = Pattern.compile("(?<!\\\\)\\{(?!#)(?!/[^/}]+})([^}]+)}");
        final Matcher matcher = fieldPattern.matcher(fragment);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                parts.add(new StaticPart(unescapeBraces(fragment.substring(lastEnd, matcher.start()))));
            }
            parts.add(new FieldReferencePart(matcher.group(1)));
            lastEnd = matcher.end();
        }

        if (lastEnd < fragment.length()) {
            parts.add(new StaticPart(unescapeBraces(fragment.substring(lastEnd))));
        }

        return parts;
    }

    private static String unescapeBraces(final String text) {
        String result = ESCAPED_OPEN_BRACE.matcher(text).replaceAll("{");
        result = ESCAPED_CLOSE_BRACE.matcher(result).replaceAll("}");
        return result;
    }

    /**
     * Formats a single JSON object according to the template parts.
     * If any field resolves to an array, produces multiple results (Cartesian product).
     */
    private static List<String> formatObject(final JsonObject jsonObject, final List<TemplatePart> templateParts) {
        final List<List<String>> resolvedParts = new ArrayList<>();
        for (final TemplatePart part : templateParts) {
            resolvedParts.add(part.resolve(jsonObject));
        }
        return cartesianProduct(resolvedParts);
    }

    /**
     * Computes the Cartesian product of all resolved part values, concatenating them into strings.
     */
    static List<String> cartesianProduct(final List<List<String>> parts) {
        List<String> results = Collections.singletonList("");
        for (final List<String> partValues : parts) {
            final List<String> newResults = new ArrayList<>(results.size() * partValues.size());
            for (final String prefix : results) {
                for (final String value : partValues) {
                    newResults.add(prefix + value);
                }
            }
            results = newResults;
        }
        return results;
    }

    /**
     * Extracts a value from a JSON object by field name or JSON pointer.
     * For arrays of primitives, returns multiple values. For arrays of objects, returns their
     * JSON string representations.
     */
    static List<String> extractField(final JsonObject jsonObject, final String fieldReference) {
        final Optional<JsonValue> optValue = extractJsonValue(jsonObject, fieldReference);

        if (!optValue.isPresent() || optValue.get().isNull()) {
            return Collections.singletonList("");
        }

        final JsonValue value = optValue.get();
        if (value.isArray()) {
            final JsonArray array = value.asArray();
            final List<String> elements = new ArrayList<>(array.getSize());
            for (final JsonValue element : array) {
                elements.add(jsonValueToString(element));
            }
            return elements.isEmpty() ? Collections.singletonList("") : elements;
        } else {
            return Collections.singletonList(jsonValueToString(value));
        }
    }

    /**
     * Extracts a JSON value from a JSON object by field name or JSON pointer.
     *
     * @param jsonObject the JSON object to extract from.
     * @param fieldReference the field name or JSON pointer (starting with {@code /}).
     * @return an Optional containing the extracted value, or empty if the field does not exist.
     */
    private static Optional<JsonValue> extractJsonValue(final JsonObject jsonObject, final String fieldReference) {
        if (fieldReference.startsWith("/")) {
            return jsonObject.getValue(JsonPointer.of(fieldReference));
        } else {
            return jsonObject.getValue(fieldReference);
        }
    }

    private static String jsonValueToString(final JsonValue value) {
        if (value.isString()) {
            return value.asString();
        } else {
            return value.toString();
        }
    }

    /**
     * Represents a part of the format template.
     */
    interface TemplatePart {

        /**
         * Resolves this template part against a JSON object.
         *
         * @param jsonObject the JSON object to resolve against
         * @return list of possible string values (multiple for array fields or sections)
         */
        List<String> resolve(JsonObject jsonObject);
    }

    /**
     * A static text part of the template.
     */
    static final class StaticPart implements TemplatePart {

        private final String text;

        StaticPart(final String text) {
            this.text = text;
        }

        @Override
        public List<String> resolve(final JsonObject jsonObject) {
            return Collections.singletonList(text);
        }

        @Override
        public String toString() {
            return "StaticPart[" + text + "]";
        }
    }

    /**
     * A field reference part of the template (e.g., {@code {fieldName}} or {@code {.}}).
     */
    static final class FieldReferencePart implements TemplatePart {

        private final String fieldReference;

        FieldReferencePart(final String fieldReference) {
            this.fieldReference = fieldReference;
        }

        @Override
        public List<String> resolve(final JsonObject jsonObject) {
            return extractField(jsonObject, fieldReference);
        }

        @Override
        public String toString() {
            return "FieldReferencePart[" + fieldReference + "]";
        }
    }

    /**
     * A section part that iterates over an array field (e.g., {@code {#items}...{/items}}).
     * <p>
     * For each element in the array:
     * <ul>
     *     <li>If the element is a JSON object, the inner template parts are resolved against it.</li>
     *     <li>If the element is a primitive, it can be referenced via {@code {.}} inside the section.</li>
     * </ul>
     * The results from all iterations are collected into a single list.
     */
    static final class SectionPart implements TemplatePart {

        private final String arrayFieldName;
        private final List<TemplatePart> innerParts;

        SectionPart(final String arrayFieldName, final List<TemplatePart> innerParts) {
            this.arrayFieldName = arrayFieldName;
            this.innerParts = innerParts;
        }

        @Override
        public List<String> resolve(final JsonObject jsonObject) {
            final Optional<JsonValue> optValue = extractJsonValue(jsonObject, arrayFieldName);

            if (!optValue.isPresent() || optValue.get().isNull()) {
                return Collections.singletonList("");
            }

            final JsonValue value = optValue.get();
            if (!value.isArray()) {
                // Not an array — treat as a single-element iteration
                return resolveElement(value);
            }

            final JsonArray array = value.asArray();
            if (array.isEmpty()) {
                return Collections.singletonList("");
            }

            final List<String> allResults = new ArrayList<>();
            for (final JsonValue element : array) {
                allResults.addAll(resolveElement(element));
            }
            return allResults;
        }

        private List<String> resolveElement(final JsonValue element) {
            if (element.isObject()) {
                return formatObject(element.asObject(), innerParts);
            } else {
                // Primitive element — create a synthetic context where {.} resolves to its value
                return resolvePrimitiveElement(element);
            }
        }

        private List<String> resolvePrimitiveElement(final JsonValue element) {
            final String elementStr = jsonValueToString(element);
            final List<List<String>> resolvedParts = new ArrayList<>();
            for (final TemplatePart part : innerParts) {
                if (part instanceof FieldReferencePart &&
                        ".".equals(((FieldReferencePart) part).fieldReference)) {
                    resolvedParts.add(Collections.singletonList(elementStr));
                } else if (part instanceof StaticPart) {
                    resolvedParts.add(part.resolve(JsonObject.empty()));
                } else {
                    // Other field references make no sense for primitives — resolve to empty
                    resolvedParts.add(Collections.singletonList(""));
                }
            }
            return cartesianProduct(resolvedParts);
        }

        @Override
        public String toString() {
            return "SectionPart[" + arrayFieldName + ", " + innerParts + "]";
        }
    }

    /**
     * Describes the signature of the {@code format('template')} function.
     */
    private static final class FormatFunctionSignature implements Signature {

        private static final FormatFunctionSignature INSTANCE = new FormatFunctionSignature();

        private final ParameterDefinition<String> templateParam;

        private FormatFunctionSignature() {
            templateParam = new TemplateParam();
        }

        @Override
        public List<ParameterDefinition<?>> getParameterDefinitions() {
            return Collections.singletonList(templateParam);
        }

        @Override
        public String toString() {
            return renderSignature();
        }
    }

    /**
     * Describes the only param of the {@code format('template')} function.
     */
    private static final class TemplateParam implements ParameterDefinition<String> {

        private TemplateParam() {
        }

        @Override
        public String getName() {
            return "template";
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public String getDescription() {
            return "The format template with {fieldName}, {/json/pointer}, or " +
                    "{#arrayField}...{/arrayField} section references to extract from each JSON object";
        }
    }

}
