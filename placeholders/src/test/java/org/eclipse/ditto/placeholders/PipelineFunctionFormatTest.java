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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineFunctionFormatTest {

    private final PipelineFunctionFormat function = new PipelineFunctionFormat();

    @Mock
    private ExpressionResolver expressionResolver;

    @After
    public void verifyExpressionResolverUnused() {
        verifyNoInteractions(expressionResolver);
    }

    @Test
    public void getName() {
        assertThat(function.getName()).isEqualTo("format");
    }

    // --- Basic field reference tests ---

    @Test
    public void singleObjectWithSimpleFields() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"reseller\":\"io.test\",\"service_provider\":\"acme\",\"customer\":\"\",\"roles\":[\"op\"]}");

        final PipelineElement result = function.apply(input,
                "('{reseller}#{service_provider}#{customer}#{roles}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("io.test#acme##op");
    }

    @Test
    public void singleObjectWithArrayField() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"tenant\":\"io.test\",\"roles\":[\"op\",\"admin\"]}");

        final PipelineElement result = function.apply(input,
                "('{tenant}:{roles}')", expressionResolver);

        assertThat(result.toStream()).containsExactlyInAnyOrder(
                "io.test:op",
                "io.test:admin"
        );
    }

    @Test
    public void multipleInputObjects() {
        final PipelineElement input = PipelineElement.resolved(Arrays.asList(
                "{\"reseller\":\"io.test\",\"service_provider\":\"acme\",\"roles\":[\"op\"]}",
                "{\"reseller\":\"io.test\",\"service_provider\":\"\",\"roles\":[\"resellerAdmin\"]}"
        ));

        final PipelineElement result = function.apply(input,
                "('{reseller}#{service_provider}#{roles}')", expressionResolver);

        assertThat(result.toStream()).containsExactly(
                "io.test#acme#op",
                "io.test##resellerAdmin"
        );
    }

    @Test
    public void missingFieldsResolveToEmptyString() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"name\":\"alice\"}");

        final PipelineElement result = function.apply(input,
                "('{name}:{missing}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("alice:");
    }

    @Test
    public void nullFieldResolvesToEmptyString() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"name\":\"alice\",\"dept\":null}");

        final PipelineElement result = function.apply(input,
                "('{name}:{dept}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("alice:");
    }

    @Test
    public void nestedFieldAccessViaJsonPointer() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"user\":{\"name\":\"alice\",\"dept\":\"eng\"}}");

        final PipelineElement result = function.apply(input,
                "('{/user/name}@{/user/dept}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("alice@eng");
    }

    @Test
    public void unresolvedInputProducesUnresolvedOutput() {
        final PipelineElement input = PipelineElement.unresolved();

        final PipelineElement result = function.apply(input,
                "('{field}')", expressionResolver);

        assertThat(result.getType()).isEqualTo(PipelineElement.Type.UNRESOLVED);
    }

    @Test
    public void escapedBracesAreRenderedLiterally() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"name\":\"alice\"}");

        final PipelineElement result = function.apply(input,
                "('\\{literal\\}:{name}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("{literal}:alice");
    }

    @Test
    public void throwsOnEmptyParameters() {
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                function.apply(PipelineElement.resolved("{}"), "()", expressionResolver)
        );
    }

    @Test
    public void throwsOnNoParameters() {
        assertThatExceptionOfType(PlaceholderFunctionSignatureInvalidException.class).isThrownBy(() ->
                function.apply(PipelineElement.resolved("{}"), "", expressionResolver)
        );
    }

    @Test
    public void multipleArrayFieldsProduceCartesianProductWithinSingleObject() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"env\":[\"dev\",\"prod\"],\"roles\":[\"op\",\"admin\"]}");

        final PipelineElement result = function.apply(input,
                "('{env}:{roles}')", expressionResolver);

        assertThat(result.toStream()).containsExactlyInAnyOrder(
                "dev:op",
                "dev:admin",
                "prod:op",
                "prod:admin"
        );
    }

    @Test
    public void templateWithOnlyStaticText() {
        final PipelineElement input = PipelineElement.resolved("{\"name\":\"alice\"}");

        final PipelineElement result = function.apply(input,
                "('static-text-only')", expressionResolver);

        assertThat(result.toStream()).containsExactly("static-text-only");
    }

    @Test
    public void invalidJsonInputIsSkipped() {
        final PipelineElement input = PipelineElement.resolved(Arrays.asList(
                "not-a-json-object",
                "{\"name\":\"alice\"}"
        ));

        final PipelineElement result = function.apply(input,
                "('{name}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("alice");
    }

    @Test
    public void nestedObjectFieldRendersAsJsonString() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"meta\":{\"key\":\"value\"},\"name\":\"alice\"}");

        final PipelineElement result = function.apply(input,
                "('{name}:{meta}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("alice:{\"key\":\"value\"}");
    }

    // --- Section syntax tests ---

    @Test
    public void sectionIteratesOverArrayOfObjects() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"tenant\":\"acme\",\"users\":[" +
                        "{\"name\":\"alice\",\"role\":\"admin\"}," +
                        "{\"name\":\"bob\",\"role\":\"viewer\"}" +
                        "]}");

        final PipelineElement result = function.apply(input,
                "('{tenant}:{#users}{name}({role}){/users}')", expressionResolver);

        assertThat(result.toStream()).containsExactly(
                "acme:alice(admin)",
                "acme:bob(viewer)"
        );
    }

    @Test
    public void sectionWithDotReferencesCurrentPrimitiveElement() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"prefix\":\"role\",\"tags\":[\"a\",\"b\",\"c\"]}");

        final PipelineElement result = function.apply(input,
                "('{prefix}:{#tags}{.}{/tags}')", expressionResolver);

        assertThat(result.toStream()).containsExactly(
                "role:a",
                "role:b",
                "role:c"
        );
    }

    @Test
    public void nestedSectionsWork() {
        // The real-world JWT use case: permissions with nested actions
        final PipelineElement input = PipelineElement.resolved(
                "{\"reseller\":\"io.test\",\"service_provider\":\"acme\"," +
                        "\"permissions\":[" +
                        "{\"resource\":\"things\",\"actions\":[\"read\",\"write\"]}," +
                        "{\"resource\":\"policies\",\"actions\":[\"read\"]}" +
                        "]}");

        final PipelineElement result = function.apply(input,
                "('{reseller}:{service_provider}:{#permissions}{resource}:{#actions}{.}{/actions}{/permissions}')",
                expressionResolver);

        assertThat(result.toStream()).containsExactly(
                "io.test:acme:things:read",
                "io.test:acme:things:write",
                "io.test:acme:policies:read"
        );
    }

    @Test
    public void sectionWithMissingArrayProducesEmptyString() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"name\":\"alice\"}");

        final PipelineElement result = function.apply(input,
                "('{name}:{#tags}{.}{/tags}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("alice:");
    }

    @Test
    public void sectionWithEmptyArrayProducesEmptyString() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"name\":\"alice\",\"tags\":[]}");

        final PipelineElement result = function.apply(input,
                "('{name}:{#tags}{.}{/tags}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("alice:");
    }

    @Test
    public void sectionOnNonArrayTreatsAsSingleElement() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"item\":{\"name\":\"thing1\",\"type\":\"sensor\"}}");

        final PipelineElement result = function.apply(input,
                "('{#item}{name}:{type}{/item}')", expressionResolver);

        assertThat(result.toStream()).containsExactly("thing1:sensor");
    }

    @Test
    public void multipleSectionsProduceCartesianProduct() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"envs\":[\"dev\",\"prod\"],\"roles\":[\"op\",\"admin\"]}");

        final PipelineElement result = function.apply(input,
                "('{#envs}{.}{/envs}:{#roles}{.}{/roles}')", expressionResolver);

        assertThat(result.toStream()).containsExactlyInAnyOrder(
                "dev:op",
                "dev:admin",
                "prod:op",
                "prod:admin"
        );
    }

    @Test
    public void sectionWithStaticTextBetweenPrimitives() {
        final PipelineElement input = PipelineElement.resolved(
                "{\"items\":[\"a\",\"b\"]}");

        final PipelineElement result = function.apply(input,
                "('prefix-{#items}[{.}]{/items}-suffix')", expressionResolver);

        assertThat(result.toStream()).containsExactly(
                "prefix-[a]-suffix",
                "prefix-[b]-suffix"
        );
    }

    @Test
    public void unclosedSectionTagThrowsIllegalArgument() {
        final PipelineElement input = PipelineElement.resolved("{\"items\":[1]}");

        assertThatIllegalArgumentException().isThrownBy(() ->
                function.apply(input, "('{#items}{.}')", expressionResolver)
        ).withMessageContaining("Unclosed section tag");
    }

    @Test
    public void sectionFieldAccessInObjectElements() {
        // Array of objects with nested fields — access via field name inside section
        final PipelineElement input = PipelineElement.resolved(
                "{\"entries\":[" +
                        "{\"key\":\"k1\",\"value\":\"v1\"}," +
                        "{\"key\":\"k2\",\"value\":\"v2\"}" +
                        "]}");

        final PipelineElement result = function.apply(input,
                "('{#entries}{key}={value}{/entries}')", expressionResolver);

        assertThat(result.toStream()).containsExactly(
                "k1=v1",
                "k2=v2"
        );
    }

    @Test
    public void complexRealWorldJwtScenario() {
        // Full real-world JWT authz scenario with nested permissions
        final PipelineElement input = PipelineElement.resolved(Arrays.asList(
                "{\"reseller\":\"io.test\",\"service_provider\":\"acme\"," +
                        "\"permissions\":[" +
                        "{\"resource\":\"things\",\"actions\":[\"read\",\"write\"]}," +
                        "{\"resource\":\"policies\",\"actions\":[\"read\"]}" +
                        "]}",
                "{\"reseller\":\"de.other\",\"service_provider\":\"techpartner\"," +
                        "\"permissions\":[" +
                        "{\"resource\":\"things\",\"actions\":[\"read\"]}" +
                        "]}"
        ));

        final PipelineElement result = function.apply(input,
                "('{reseller}:{service_provider}:{#permissions}{resource}:{#actions}{.}{/actions}{/permissions}')",
                expressionResolver);

        assertThat(result.toStream()).containsExactly(
                "io.test:acme:things:read",
                "io.test:acme:things:write",
                "io.test:acme:policies:read",
                "de.other:techpartner:things:read"
        );
    }

}
