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

// mirrors RegexPatterns.NAMESPACE_REGEX of Ditto, without the empty namespace
const NAMESPACE_REGEX = /^[a-zA-Z]\w*(?:[.-][a-zA-Z]\w*)*$/;

export function isValidNamespace(namespace: string): boolean {
  return NAMESPACE_REGEX.test(namespace);
}

/**
 * Parses a comma separated list of namespaces into a list without blanks and duplicates
 * @param {string} commaSeparated e.g. "org.eclipse.ditto, com.example"
 * @return {string[]} the namespaces
 */
export function parseNamespaces(commaSeparated: string | undefined | null): string[] {
  return [...new Set((commaSeparated ?? '')
      .split(',')
      .map((namespace) => namespace.trim())
      .filter((namespace) => namespace !== ''))];
}

/**
 * Determines the namespaces to search in
 * @param {string[]} available the namespaces available for selection
 * @param {string[]} selected the selected namespaces, undefined if the user never picked any
 * @return {string[]} the selected namespaces which are still available, all available ones if nothing was picked yet
 */
export function activeNamespaces(available: string[], selected: string[] | undefined): string[] {
  if (selected === undefined) {
    return available;
  }
  return available.filter((namespace) => selected.includes(namespace));
}

/**
 * Builds the query parameter for the Things search
 * @param {string[]} namespaces namespaces to search in
 * @return {string} the "namespaces" query parameter or an empty string if no namespace is given
 */
export function namespacesQueryParameter(namespaces: string[]): string {
  return namespaces.length > 0 ?
    'namespaces=' + namespaces.map(encodeURIComponent).join(',') :
    '';
}
