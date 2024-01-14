/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import { BasicFilters, Term, FilterType } from "../../modules/utils/basicFilters";

describe("create JSONpath", () => {
  test("One property equals one keyword", () => {
    expect(new BasicFilters()
        .addPropEq('quick', 'brown')
        .createJsonPath())
        .toBe('$[?(@.quick=="brown")]');
  });

  test("One property equals two keywords", () => {
    expect(new BasicFilters()
        .addPropEq('quick', 'brown')
        .addPropEq('quick', 'fox')
        .createJsonPath())
      .toBe('$[?(@.quick=="fox"||@.quick=="brown")]');
  });

  test("Two properties equal a keyword", () => {
    expect(new BasicFilters()
        .addPropEq('quick', 'brown')
        .addPropEq('jumps', 'fox')
        .createJsonPath())
      .toBe('$[?((@.quick=="brown")&&(@.jumps=="fox"))]');
  });

  test("One property like one keyword", () => {
    expect(new BasicFilters()
        .addPropLike('quick', 'brown')
        .createJsonPath())
        .toBe('$[?(/brown/.test(@.quick))]');
  });

  test("One property like two keywords", () => {
    expect(new BasicFilters()
        .addPropLike('quick', 'brown')
        .addPropLike('quick', 'fox')
        .createJsonPath())
        .toBe('$[?(/fox/.test(@.quick)||/brown/.test(@.quick))]');
  });

  test("Two properties like a keyword", () => {
    expect(new BasicFilters()
        .addPropLike('quick', 'brown')
        .addPropLike('jumps', 'fox')
        .createJsonPath())
      .toBe('$[?((/brown/.test(@.quick))&&(/fox/.test(@.jumps)))]');
  });

  test("Full like keyword", () => {
    expect(new BasicFilters()
        .setAllLike('quick')
        .createJsonPath())
      .toBe('$[?(/quick/.test(JSON.stringify(@)))]');
  });

  test("Full like keyword and one property equals", () => {
    expect(new BasicFilters()
        .setAllLike('fox')
        .addPropEq('quick', 'brown')
        .createJsonPath())
      .toBe('$[?((/fox/.test(JSON.stringify(@)))&&(@.quick=="brown"))]');
  });

  test("One property equals one property like", () => {
    expect(new BasicFilters()
        .addPropEq('quick', 'brown')
        .addPropLike('jumps', 'fox')
        .createJsonPath())
      .toBe('$[?((@.quick=="brown")&&(/fox/.test(@.jumps)))]');
  });

  test("One property equals one property like and full search", () => {
    expect(new BasicFilters()
        .setAllLike('dog')
        .addPropEq('quick', 'brown')
        .addPropLike('jumps', 'fox')
        .createJsonPath())
      .toBe('$[?((/dog/.test(JSON.stringify(@)))&&(@.quick=="brown")&&(/fox/.test(@.jumps)))]');
  });

  test("One property like one keyword escaped", () => {
    expect(new BasicFilters()
        .addPropLike('quick', '/features/brown')
        .createJsonPath())
        .toBe('$[?(/\\/features\\/brown/.test(@.quick))]');
  });

});

describe("Term from string", () => {
  test("Property equals", () => {
    expect(Term.fromString('quick:brown'))
        .toEqual(new Term(FilterType.PROP_EQ, 'brown', 'quick'))
  });

  test("Property like", () => {
    expect(Term.fromString('quick~brown'))
        .toEqual(new Term(FilterType.PROP_LIKE, 'brown', 'quick'))
  });

  test("Find in root", () => {
    expect(Term.fromString('quick'))
        .toEqual(new Term(FilterType.PROP_LIKE, 'quick'))
  });
});