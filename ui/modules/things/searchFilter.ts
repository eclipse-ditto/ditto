/* eslint-disable require-jsdoc */
/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
// @ts-check

import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import * as SearchNamespaces from './searchNamespaces.js';
import * as ThingsSearch from './thingsSearch.js';

const filterExamples = [
  'eq(attributes/location,"kitchen")',
  'ge(thingId,"myThing1")',
  'gt(_created,"2020-08-05T12:17")',
  'exists(features/featureId)',
  'empty(features/temperature/properties/list)',
  'and(eq(attributes/location,"kitchen"),eq(attributes/color,"red"))',
  'or(eq(attributes/location,"kitchen"),eq(attributes/location,"living-room"))',
  'like(attributes/key1,"known-chars-at-start*")',
];

const filterHistory = [];

let keyStrokeTimeout;

const FILTER_PLACEHOLDER = '...';

let autoCompleteJS;

const dom = {
  favIcon: null,
  searchFilterEdit: null,
  searchThings: null,
  searchFavorite: null,
  tabThings: null,
  pinnedThings: null,
  searchNamespacesCount: null,
  searchNamespacesList: null,
  searchNamespaceAdd: null,
  searchNamespaceAddButton: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  dom.pinnedThings.onclick = ThingsSearch.pinnedTriggered;

  autoCompleteJS = Utils.createAutoComplete('#searchFilterEdit', createFilterList, 'Search for Things...');
  autoCompleteJS.input.addEventListener('selection', (event) => {
    const selection = event.detail.selection.value;
    fillSearchFilterEditAndSearch(selection.rql);
  });

  dom.searchThings.onclick = () => {
    ThingsSearch.searchTriggered(dom.searchFilterEdit.value, () => fillHistory(dom.searchFilterEdit.value));
  };

  dom.searchFavorite.onclick = async (e) => {
    e.preventDefault();
    let isValidQuery = await toggleFilterFavorite(dom.searchFilterEdit.value);
    if (isValidQuery) {
      dom.favIcon.classList.toggle('bi-star');
      dom.favIcon.classList.toggle('bi-star-fill');
    }
  };

  dom.searchFilterEdit.onkeyup = (event) => {
    if ((event.key === 'Enter' || event.code === 13) && dom.searchFilterEdit.value.indexOf(FILTER_PLACEHOLDER) < 0) {
      ThingsSearch.searchTriggered(dom.searchFilterEdit.value, () => fillHistory(dom.searchFilterEdit.value));
    } else {
      clearTimeout(keyStrokeTimeout);
      keyStrokeTimeout = setTimeout(checkIfFavorite, 1000);
    }
  };

  dom.searchFilterEdit.onchange = () => {
    ThingsSearch.removeMoreFromThingList();
  };

  dom.searchNamespacesList.onchange = onSearchNamespaceToggled;
  dom.searchNamespacesList.onclick = onSearchNamespaceRemoveClicked;
  dom.searchNamespaceAddButton.onclick = addSearchNamespace;
  dom.searchNamespaceAdd.onkeydown = (event) => {
    if (event.key === 'Enter') {
      // the surrounding form would otherwise submit via the favorite button
      event.preventDefault();
      addSearchNamespace();
    }
  };

  dom.searchFilterEdit.focus();
}

/**
 * Callback to initialize searchFilters if the environment changed
 */
function onEnvironmentChanged() {
  if (!Environments.current().filterList) {
    Environments.current().filterList = [];
  }
  if (!Environments.current().pinnedThings) {
    Environments.current().pinnedThings = [];
  }
  updateSearchNamespaces();
}

function availableSearchNamespaces(): string[] {
  return SearchNamespaces.parseNamespaces(Environments.current().searchNamespaces);
}

function updateSearchNamespaces() {
  const active = ThingsSearch.activeSearchNamespaces();
  dom.searchNamespacesCount.textContent = active.length > 0 ? String(active.length) : '';
  dom.searchNamespacesList.textContent = '';

  const available = availableSearchNamespaces();
  if (available.length === 0) {
    const hint = document.createElement('div');
    hint.classList.add('form-text', 'mt-0');
    hint.textContent = 'No namespaces configured, searching in all namespaces';
    dom.searchNamespacesList.append(hint);
  }
  available.forEach((namespace) => {
    const row = document.createElement('div');
    row.classList.add('d-flex', 'align-items-center');
    row.dataset.namespace = namespace;

    const check = document.createElement('div');
    check.classList.add('form-check', 'flex-grow-1', 'mb-0');
    const checkBox = document.createElement('input');
    checkBox.classList.add('form-check-input');
    checkBox.type = 'checkbox';
    checkBox.id = `searchNamespace_${namespace}`;
    checkBox.checked = active.includes(namespace);
    const label = document.createElement('label');
    label.classList.add('form-check-label', 'text-break');
    label.htmlFor = checkBox.id;
    label.textContent = namespace;
    check.append(checkBox, label);

    const removeButton = document.createElement('button');
    removeButton.type = 'button';
    removeButton.classList.add('btn', 'btn-sm', 'btn-link', 'text-secondary', 'p-0', 'ms-2');
    removeButton.title = 'Remove namespace';
    removeButton.innerHTML = '<i class="bi bi-x-lg"></i>';

    row.append(check, removeButton);
    dom.searchNamespacesList.append(row);
  });
}

async function onSearchNamespaceToggled() {
  const selected = Array.from(dom.searchNamespacesList.querySelectorAll('input:checked'))
      .map((checkBox: HTMLElement) => checkBox.closest<HTMLElement>('[data-namespace]').dataset.namespace);
  await searchNamespacesChanged(availableSearchNamespaces(), selected);
}

async function onSearchNamespaceRemoveClicked(event: MouseEvent) {
  const removeButton = (event.target as HTMLElement).closest('button');
  if (!removeButton) {
    return;
  }
  const namespace = removeButton.closest<HTMLElement>('[data-namespace]').dataset.namespace;
  await searchNamespacesChanged(
      availableSearchNamespaces().filter((ns) => ns !== namespace),
      ThingsSearch.activeSearchNamespaces().filter((ns) => ns !== namespace));
}

async function addSearchNamespace() {
  const namespace = dom.searchNamespaceAdd.value.trim();
  Utils.assert(SearchNamespaces.isValidNamespace(namespace),
      'Namespace must be in Java package notation, e.g. org.eclipse.ditto',
      dom.searchNamespaceAdd);
  const available = availableSearchNamespaces();
  const active = ThingsSearch.activeSearchNamespaces();
  dom.searchNamespaceAdd.value = '';
  await searchNamespacesChanged(
      available.includes(namespace) ? available : [...available, namespace],
      active.includes(namespace) ? active : [...active, namespace]);
}

async function searchNamespacesChanged(available: string[], selected: string[]) {
  Environments.current().searchNamespaces = available.join(',');
  Environments.current().selectedSearchNamespaces = selected;
  await Environments.environmentsJsonChanged(false, 'searchNamespaces');
}

export function fillSearchFilterEdit(fillString: string) {
  dom.searchFilterEdit.value = fillString;
}

export function fillSearchFilterEditAndSearch(fillString: string) {
  fillSearchFilterEdit(fillString);

  checkIfFavorite();
  const filterEditNeeded = Utils.checkAndMarkInInput(dom.searchFilterEdit, FILTER_PLACEHOLDER);
  if (!filterEditNeeded) {
    ThingsSearch.searchTriggered(dom.searchFilterEdit.value, () => null);
  }
}

async function createFilterList(query) {
  const date24h = new Date();
  const date1h = new Date();
  const date1m = new Date();
  date24h.setDate(date24h.getDate() - 1);
  date1h.setHours(date1h.getHours() - 1);
  date1m.setMinutes(date1m.getMinutes() -1);

  return [
    {
      label: 'Created since 1m',
      rql: `gt(_created,"${date1m.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Created since 1h',
      rql: `gt(_created,"${date1h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Created since 24h',
      rql: `gt(_created,"${date24h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Modified since 1m',
      rql: `gt(_modified,"${date1m.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Modified since 1h',
      rql: `gt(_modified,"${date1h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: 'Modified since 24h',
      rql: `gt(_modified,"${date24h.toISOString()}")`,
      group: 'Time',
    },
    {
      label: `thingId = ${FILTER_PLACEHOLDER}`,
      rql: `eq(thingId,"${FILTER_PLACEHOLDER}")`,
      group: 'ThingId',
    },
    {
      label: `thingId ~ ${FILTER_PLACEHOLDER}`,
      rql: `like(thingId,"${FILTER_PLACEHOLDER}*")`,
      group: 'ThingId',
    },
    ...(Environments.current().filterList ?? []).map((f) => ({label: f, rql: f, group: 'Favorite'})),
    ...(Environments.current().fieldList ?? []).map((f) => ({
      label: `${f.label} = ${FILTER_PLACEHOLDER}`,
      rql: `eq(${f.path},"${FILTER_PLACEHOLDER}")`,
      group: 'Field',
    })),
    ...(Environments.current().fieldList ?? []).map((f) => ({
      label: `${f.label} ~ ${FILTER_PLACEHOLDER}`,
      rql: `like(${f.path},"*${FILTER_PLACEHOLDER}*")`,
      group: 'Field',
    })),
    ...filterHistory.map((f) => ({label: f, rql: f, group: 'Recent'})),
    ...filterExamples.map((f) => ({label: f, rql: f, group: 'Example'})),
  ];
}

/**
 * Adds or removes the given filter from the list of search filters
 * @param {string} filter filter
 * @return {boolean} true if the filter was toggled
 */
async function toggleFilterFavorite(filter: string): Promise<boolean> {
  if (!filter || filter === '') {
    return false;
  }
  const i = Environments.current().filterList.indexOf(filter);
  if (i >= 0) {
    Environments.current().filterList.splice(i, 1);
  } else {
    Environments.current().filterList.push(filter);
  }
  await Environments.environmentsJsonChanged(false, 'filterList');
  return true;
}

/**
 * Initializes the UI for the favicon dependent on wether the search filter is in the search filters
 */
function checkIfFavorite() {
  if (Environments.current().filterList.indexOf(dom.searchFilterEdit.value) >= 0) {
    dom.favIcon.classList.replace('bi-star', 'bi-star-fill');
  } else {
    dom.favIcon.classList.replace('bi-star-fill', 'bi-star');
  }
}

function fillHistory(filter) {
  if (!filterHistory.includes(filter)) {
    filterHistory.unshift(filter);
  }
}

