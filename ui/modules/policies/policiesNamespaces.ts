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

import * as API from '../api.js';
import * as Utils from '../utils.js';
import { CrudOperation, CrudToolbar } from '../utils/crudToolbar.js';
import * as Policies from './policies.js';
import * as PolicyEntries from './policiesEntries.js';

let selectedNamespace: string;

type DomElements = {
  tbodyPolicyNamespaces: HTMLTableElement,
  tableValidationNamespaces: HTMLInputElement,
  crudNamespace: CrudToolbar,
}

let dom: DomElements = {
  tbodyPolicyNamespaces: null,
  tableValidationNamespaces: null,
  crudNamespace: null,
};

export function ready() {
  Utils.getAllElementsById(dom);
  PolicyEntries.observable.addChangeListener(onEntryChanged);

  Utils.addValidatorToTable(dom.tbodyPolicyNamespaces, dom.tableValidationNamespaces);

  dom.crudNamespace.editDisabled = true;
  dom.crudNamespace.addEventListener('onCreateClick', onCreateNamespaceClick);
  dom.crudNamespace.addEventListener('onUpdateClick', onUpdateNamespaceClick);
  dom.crudNamespace.addEventListener('onDeleteClick', onDeleteNamespaceClick);
  dom.crudNamespace.addEventListener('onEditToggle', onEditToggleNamespace);

  dom.tbodyPolicyNamespaces.onclick = onPolicyNamespacesClick;
}

function onPolicyNamespacesClick(event: MouseEvent): void {
  const target = event.target as HTMLElement;
  const id = target.parentElement?.id;
  if (!id) {
    return;
  }
  if (selectedNamespace === id) {
    selectedNamespace = null;
    dom.crudNamespace.idValue = null;
  } else {
    selectedNamespace = id;
    dom.crudNamespace.idValue = selectedNamespace;
  }
}

function onCreateNamespaceClick() {
  PolicyEntries.validateSelected();
  Utils.assert(dom.crudNamespace.idValue, 'Please enter a namespace pattern',
      dom.crudNamespace.validationElement);
  const entry = Policies.thePolicy.entries[PolicyEntries.selectedEntry];
  const existing = entry.namespaces ?? [];
  Utils.assert(!existing.includes(dom.crudNamespace.idValue),
      'Namespace pattern already present', dom.crudNamespace.validationElement);
  selectedNamespace = dom.crudNamespace.idValue;
  putEntryWithNamespaces([...existing, selectedNamespace],
      Policies.finishEditing(dom.crudNamespace, CrudOperation.CREATE));
}

function onUpdateNamespaceClick() {
  PolicyEntries.validateSelected();
  Utils.assert(selectedNamespace, 'Please select a namespace', dom.tableValidationNamespaces);
  Utils.assert(dom.crudNamespace.idValue, 'Please enter a namespace pattern',
      dom.crudNamespace.validationElement);
  const entry = Policies.thePolicy.entries[PolicyEntries.selectedEntry];
  const existing = entry.namespaces ?? [];
  const next = existing.map((ns) => (ns === selectedNamespace ? dom.crudNamespace.idValue : ns));
  selectedNamespace = dom.crudNamespace.idValue;
  putEntryWithNamespaces(next, Policies.finishEditing(dom.crudNamespace, CrudOperation.UPDATE));
}

function onDeleteNamespaceClick() {
  PolicyEntries.validateSelected();
  Utils.assert(selectedNamespace, 'Please select a namespace', dom.tableValidationNamespaces);
  const entry = Policies.thePolicy.entries[PolicyEntries.selectedEntry];
  const existing = entry.namespaces ?? [];
  const next = existing.filter((ns) => ns !== selectedNamespace);
  putEntryWithNamespaces(next, Policies.finishEditing(dom.crudNamespace, CrudOperation.DELETE));
}

// Always read the latest entry body at PUT time — never cache locally — to avoid races with concurrent
// edits in sibling sections. Empty list is normalised to "field absent" so loaded policies that never
// opted into namespace gating round-trip unchanged.
function putEntryWithNamespaces(namespaces: string[], onSuccess: (value: any) => any) {
  const entry = { ...Policies.thePolicy.entries[PolicyEntries.selectedEntry] };
  if (namespaces.length === 0) {
    delete entry.namespaces;
  } else {
    entry.namespaces = namespaces;
  }
  API.callDittoREST('PUT',
      `/policies/${Policies.thePolicy.policyId}/entries/${PolicyEntries.selectedEntry}`,
      entry
  ).then(onSuccess);
}

function onEditToggleNamespace(event: CustomEvent) {
  if (event.detail.isCancel) {
    dom.crudNamespace.idValue = selectedNamespace;
  }
}

function onEntryChanged(entryLabel: string) {
  selectedNamespace = null;
  dom.tbodyPolicyNamespaces.textContent = '';
  dom.crudNamespace.idValue = null;
  dom.crudNamespace.editDisabled = (entryLabel === null);

  if (Policies.thePolicy && entryLabel) {
    const namespaces = Policies.thePolicy.entries[entryLabel].namespaces ?? [];
    namespaces.forEach((ns) => {
      Utils.addTableRow(dom.tbodyPolicyNamespaces, ns, false);
    });
  }
}
