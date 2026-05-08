/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import { Observable } from '../utils/observable.js';
import * as Policies from './policies.js';

export let observable = Observable();

export let selectedEntry: string;

type AdditionsMode = 'default' | 'denyAll' | 'allowOnly';

type DomElements = {
  tbodyPolicyEntries: HTMLTableElement,
  tableValidationEntries: HTMLInputElement,
  selectImportable: HTMLSelectElement,
  crudEntry: CrudToolbar,
  selectAllowedAdditionsMode: HTMLSelectElement,
  checkboxAdditionSubjects: HTMLInputElement,
  checkboxAdditionResources: HTMLInputElement,
  checkboxAdditionNamespaces: HTMLInputElement,
  validationAllowedAdditions: HTMLInputElement,
}

let dom: DomElements = {
  tbodyPolicyEntries: null,
  tableValidationEntries: null,
  selectImportable: null,
  crudEntry: null,
  selectAllowedAdditionsMode: null,
  checkboxAdditionSubjects: null,
  checkboxAdditionResources: null,
  checkboxAdditionNamespaces: null,
  validationAllowedAdditions: null,
};

export function ready() {
  
  Utils.getAllElementsById(dom);
  Policies.observable.addChangeListener(onPolicyChanged);

  Utils.addValidatorToTable(dom.tbodyPolicyEntries, dom.tableValidationEntries);

  dom.crudEntry.editDisabled = true;
  dom.crudEntry.addEventListener('onCreateClick', onCreateEntryClick);
  dom.crudEntry.addEventListener('onDeleteClick', onDeleteEntryClick);
  dom.crudEntry.addEventListener('onUpdateClick', onUpdateEntryClick);
  dom.crudEntry.addEventListener('onEditToggle', onToggleEditEntry);

  dom.selectAllowedAdditionsMode.addEventListener('change', onAdditionsModeChange);

  dom.tbodyPolicyEntries.onclick = onPolicyEntriesClick;
}

function onToggleEditEntry(event) {
  const editing: boolean = event.detail.isEditing;
  dom.selectImportable.disabled = !editing;
  setAllowedAdditionsControlsDisabled(!editing);
  if (event.detail.isCancel) {
    updateEditors(selectedEntry);
  }
}

function setAllowedAdditionsControlsDisabled(disabled: boolean) {
  dom.selectAllowedAdditionsMode.disabled = disabled;
  // The kind-checkboxes are only meaningful when "Allow only" is the active mode.
  const allowOnlyActive = !disabled && dom.selectAllowedAdditionsMode.value === 'allowOnly';
  dom.checkboxAdditionSubjects.disabled = !allowOnlyActive;
  dom.checkboxAdditionResources.disabled = !allowOnlyActive;
  dom.checkboxAdditionNamespaces.disabled = !allowOnlyActive;
}

function onAdditionsModeChange() {
  // Re-evaluate checkbox disabled state when the user toggles between modes.
  setAllowedAdditionsControlsDisabled(false);
  dom.validationAllowedAdditions.classList.remove('is-invalid');
}

// Centralised absent-vs-empty discipline. Returns the value to write — `null` means "delete the field".
// This is the single most error-prone aspect of allowedAdditions: the JSON encoding distinguishes
// absent (no restriction) from `[]` (deny all), and the mode select is the source of truth.
function readAllowedAdditionsFromUI(): Policies.AdditionKind[] | null {
  const mode = dom.selectAllowedAdditionsMode.value as AdditionsMode;
  if (mode === 'default') {
    return null;
  }
  if (mode === 'denyAll') {
    return [];
  }
  const kinds: Policies.AdditionKind[] = [];
  if (dom.checkboxAdditionSubjects.checked) kinds.push('subjects');
  if (dom.checkboxAdditionResources.checked) kinds.push('resources');
  if (dom.checkboxAdditionNamespaces.checked) kinds.push('namespaces');
  return kinds;
}

function applyAllowedAdditions(entry: Policies.PolicyEntry) {
  const value = readAllowedAdditionsFromUI();
  if (value === null) {
    delete entry.allowedAdditions;
  } else {
    entry.allowedAdditions = value;
  }
}

function loadAllowedAdditionsIntoUI(allowedAdditions: Policies.AdditionKind[] | undefined) {
  dom.checkboxAdditionSubjects.checked = false;
  dom.checkboxAdditionResources.checked = false;
  dom.checkboxAdditionNamespaces.checked = false;
  if (allowedAdditions === undefined) {
    dom.selectAllowedAdditionsMode.value = 'default';
  } else if (allowedAdditions.length === 0) {
    dom.selectAllowedAdditionsMode.value = 'denyAll';
  } else {
    dom.selectAllowedAdditionsMode.value = 'allowOnly';
    allowedAdditions.forEach((k) => {
      if (k === 'subjects') dom.checkboxAdditionSubjects.checked = true;
      else if (k === 'resources') dom.checkboxAdditionResources.checked = true;
      else if (k === 'namespaces') dom.checkboxAdditionNamespaces.checked = true;
    });
  }
  setAllowedAdditionsControlsDisabled(!dom.crudEntry.isEditing);
}

function onPolicyEntriesClick(event) {
  if (selectedEntry === event.target.textContent) {
    selectedEntry = null;
  } else {
    selectedEntry = event.target.textContent;
  }
  setEntry(selectedEntry);
}

function onCreateEntryClick() {
  // validations(true);
  Utils.assert(dom.crudEntry.idValue, 'Please enter a label for the entry', dom.crudEntry.validationElement);
  Utils.assert(!Object.keys(Policies.thePolicy.entries).includes(dom.crudEntry.idValue),
      `Entry with label ${dom.crudEntry.idValue} already exists`, dom.crudEntry.validationElement);
  validateAllowedAdditionsSelection();
  selectedEntry = dom.crudEntry.idValue;
  const newEntry: Policies.PolicyEntry = {
    subjects: {},
    resources: {},
    importable: dom.selectImportable.value as Policies.ImportableMode,
  };
  applyAllowedAdditions(newEntry);
  putOrDeletePolicyEntry(dom.crudEntry.idValue, newEntry,
      Policies.finishEditing(dom.crudEntry, CrudOperation.CREATE));
}

function onUpdateEntryClick() {
  validateAllowedAdditionsSelection();
  // Spread-copy: never mutate Policies.thePolicy.entries[...] in place. If the PUT 4xx's, an
  // in-place mutation would leave the cache in a state that doesn't match the server, and a
  // subsequent Cancel would re-paint the editors from that corrupted state. Mirrors the pattern
  // already used in policiesNamespaces.ts and policiesReferences.ts.
  const entry = { ...Policies.thePolicy.entries[selectedEntry] };
  entry.importable = dom.selectImportable.value as Policies.ImportableMode;
  applyAllowedAdditions(entry);
  putOrDeletePolicyEntry(selectedEntry, entry, Policies.finishEditing(dom.crudEntry, CrudOperation.UPDATE));
}

function validateAllowedAdditionsSelection() {
  const allowOnlyEmpty = dom.selectAllowedAdditionsMode.value === 'allowOnly' &&
      !dom.checkboxAdditionSubjects.checked &&
      !dom.checkboxAdditionResources.checked &&
      !dom.checkboxAdditionNamespaces.checked;
  Utils.assert(!allowOnlyEmpty,
      'Pick at least one kind, or switch to "Deny all".',
      dom.validationAllowedAdditions);
}

function onDeleteEntryClick() {
  // validations(false, true);
  validateSelected();
  Utils.confirm(`Are you sure you want to delete policy entry<br>'${dom.crudEntry.idValue}'?`, 'Delete', () => {
    putOrDeletePolicyEntry(selectedEntry, null, Policies.finishEditing(dom.crudEntry, CrudOperation.DELETE));
  });
}

function putOrDeletePolicyEntry(entry, value, onSuccess) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${Policies.thePolicy.policyId}/entries/${entry}`,
      value
  ).then(onSuccess);
};

function onPolicyChanged(policy: Policies.Policy) {
  dom.tbodyPolicyEntries.textContent = '';
  dom.crudEntry.idValue = null;
  dom.crudEntry.editDisabled = (policy === null);
  
  if (policy) {
    let policyHasEntry = false;
    Object.keys(policy.entries).forEach((key) => {
      Utils.addTableRow(dom.tbodyPolicyEntries, key, key === selectedEntry);
      if (key === selectedEntry) {
        setEntry(key);
        policyHasEntry = true;
      }
    });
    if (!policyHasEntry) {
      selectedEntry = null;
      setEntry(null);
    }
  } else {
    setEntry(null);
  }
}

function setEntry(entryLabel: string) {
  updateEditors(entryLabel);
  observable.notifyAll(selectedEntry);
};

function updateEditors(entryLabel: string) {
  dom.crudEntry.idValue = entryLabel;
  const entry = entryLabel ? Policies.thePolicy.entries[entryLabel] : null;
  dom.selectImportable.value = (entry && entry.importable) || 'implicit';
  loadAllowedAdditionsIntoUI(entry?.allowedAdditions);
}


export function validateSelected() {
  Utils.assert(selectedEntry, 'Please select an entry', dom.tableValidationEntries);
}

