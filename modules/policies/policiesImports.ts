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
import * as Policies from './policies.js';


let selectedImport: string;

type DomElements = {
  tbodyPolicyImports: HTMLTableElement,
  tbodyPolicyImportEntries: HTMLTableElement,
  crudImport: CrudToolbar,
  tbodyPolicyTransitiveImports: HTMLTableElement,
  inputTransitiveImport: HTMLInputElement,
  buttonAddTransitiveImport: HTMLButtonElement,
}

let dom: DomElements = {
  tbodyPolicyImports: null,
  tbodyPolicyImportEntries: null,
  crudImport: null,
  tbodyPolicyTransitiveImports: null,
  inputTransitiveImport: null,
  buttonAddTransitiveImport: null,
} ;

// let importEditor: ace.Editor;

export function ready() {
  Utils.getAllElementsById(dom);
  Policies.observable.addChangeListener(onPolicyChanged);

  // Utils.addValidatorToTable(dom.tbodyPolicySubjects, dom.tableValidationSubjects);

  // importEditor = Utils.createAceEditor('importEditor', 'ace/mode/json');

  dom.tbodyPolicyImports.onclick = onPolicyImportsClick;

  dom.crudImport.editDisabled = true;
  dom.crudImport.addEventListener('onCreateClick', onCreatePolicyImportClick);
  dom.crudImport.addEventListener('onUpdateClick', onUpdatePolicyImportClick);
  dom.crudImport.addEventListener('onDeleteClick', onDeletePolicyImportClick);
  dom.crudImport.addEventListener('onEditToggle', onEditToggleImport);
  dom.crudImport.addEventListener('onIdValueChange', onIdValueChange);

  dom.buttonAddTransitiveImport.addEventListener('click', onAddTransitiveImportClick);
  dom.inputTransitiveImport.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !dom.inputTransitiveImport.disabled) {
      e.preventDefault();
      onAddTransitiveImportClick();
    }
  });
}

async function onIdValueChange() {
  await setImport(dom.crudImport.idValue);
  setExplicitCheckboxesDisabledState(false);
}

function onPolicyImportsClick(event: MouseEvent): any {
  const target = event.target as HTMLElement;
  if (selectedImport === target.parentElement.id) {
    selectedImport = null;
  } else {
    selectedImport = target.parentElement.id;
  }
  setImport(selectedImport);
}

function modifyImport(key: string, value: object, onSuccess: (value: any) => any) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${Policies.thePolicy.policyId}/imports/${key}`, value
  ).then(onSuccess);
};

function onCreatePolicyImportClick() {
  selectedImport = dom.crudImport.idValue;
  modifyImport(selectedImport, importValueFromCheckboxes(), Policies.finishEditing(dom.crudImport, CrudOperation.CREATE));
}

function onUpdatePolicyImportClick() {
  modifyImport(selectedImport, importValueFromCheckboxes(), Policies.finishEditing(dom.crudImport, CrudOperation.UPDATE));
}

function onDeletePolicyImportClick() {
  Utils.confirm(`Are you sure you want to delete policy import<br>'${dom.crudImport.idValue}'?`, 'Delete', () => {
    modifyImport(selectedImport, null, Policies.finishEditing(dom.crudImport, CrudOperation.DELETE));
  });
}

function importValueFromCheckboxes(): Policies.PolicyImport {
  const importValue: Policies.PolicyImport = {};
  const checkedBoxes = document.querySelectorAll('#tbodyPolicyImportEntries input:checked[data-importable="explicit"]');
  if (checkedBoxes.length > 0) {
    importValue.entries = Array.from(checkedBoxes).map((checkbox) => checkbox.id);
  }
  // transitiveImports is an explicit whitelist of additional imports to resolve. Absent and `[]`
  // are semantically equivalent (no transitives resolved either way), so we omit the field when
  // empty rather than writing `[]`.
  const transitive = readTransitiveImportsFromTable();
  if (transitive.length > 0) {
    importValue.transitiveImports = transitive;
  }
  return importValue;
}

function readTransitiveImportsFromTable(): string[] {
  return Array.from(dom.tbodyPolicyTransitiveImports.querySelectorAll('tr'))
      .map((row: HTMLTableRowElement) => row.id)
      .filter((id) => id);
}

function onEditToggleImport(event: CustomEvent) {
  const editing: boolean = event.detail.isEditing;
  setExplicitCheckboxesDisabledState(!editing);
  setTransitiveImportsControlsDisabled(!editing);
  if (event.detail.isCancel) {
    setImport(selectedImport);
  }
}

function setExplicitCheckboxesDisabledState(disabled: boolean) {
  document.querySelectorAll('#tbodyPolicyImportEntries input[data-importable="explicit"]')
    .forEach((e: HTMLInputElement) => e.disabled = disabled);
}

function setTransitiveImportsControlsDisabled(disabled: boolean) {
  dom.inputTransitiveImport.disabled = disabled;
  dom.buttonAddTransitiveImport.disabled = disabled;
  // Toggle ONLY the remove button (the .bi-x-lg icon) — navigate stays enabled regardless of
  // edit mode so the user can inspect a linked policy without entering edit mode first.
  dom.tbodyPolicyTransitiveImports.querySelectorAll('button:has(.bi-x-lg)')
      .forEach((b: HTMLButtonElement) => b.disabled = disabled);
}

function onAddTransitiveImportClick() {
  const value = dom.inputTransitiveImport.value.trim();
  Utils.assert(value, 'Please enter a policy ID', dom.inputTransitiveImport);
  Utils.assert(/^[^\s:]+:[^\s:]+/.test(value),
      'Expected a policy ID in the form "namespace:name"', dom.inputTransitiveImport);
  const existing = readTransitiveImportsFromTable();
  Utils.assert(!existing.includes(value),
      'Policy ID already in the list', dom.inputTransitiveImport);
  appendTransitiveImportRow(value, false);
  dom.inputTransitiveImport.value = '';
  dom.inputTransitiveImport.focus();
}

function appendTransitiveImportRow(policyId: string, disabled: boolean) {
  const row = Utils.addTableRow(dom.tbodyPolicyTransitiveImports, policyId, false);
  Utils.addActionToRow(row, 'bi-arrow-up-right-square', getNavigatePolicyAction(policyId), 'Open policy');
  Utils.addActionToRow(row, 'bi-x-lg', () => {
    if (dom.inputTransitiveImport.disabled) {
      return;
    }
    Utils.confirm(`Remove transitive import<br>'${policyId}'?`, 'Remove', () => {
      row.remove();
    });
  }, 'Remove');
  // Action buttons are the trailing cells; disable only the remove button when not editing — keep
  // the navigate button available so users can inspect the linked policy without entering edit mode.
  const buttons = row.querySelectorAll('button');
  const removeBtn = buttons[buttons.length - 1] as HTMLButtonElement | undefined;
  if (removeBtn) removeBtn.disabled = disabled;
}

function getNavigatePolicyAction(policyId: string) {
  return (_evt: Event) => {
    API.callDittoREST('GET', '/policies/' + policyId).then(async (targetPolicy: Policies.Policy) => {
      await Policies.setThePolicy(targetPolicy);
    });
  };
}

async function onPolicyChanged(policy: Policies.Policy) {
  dom.tbodyPolicyImports.textContent = '';
  dom.crudImport.idValue = null;
  dom.crudImport.editDisabled = (policy === null);
  
  if (policy) {
    let policyHasImport = false;
    for (const key of Object.keys(policy.imports)) {
      const row = Utils.addTableRow(dom.tbodyPolicyImports, key, key === selectedImport);
      Utils.addActionToRow(row, 'bi-arrow-up-right-square', getNavigatePolicyAction(key), 'Open policy');
      if (key === selectedImport) {
        await setImport(key);
        policyHasImport = true;
      }
    }
    if (!policyHasImport) {
      selectedImport = null;
      await setImport(null);
    }
  } else {
    await setImport(null);
  }
}

async function setImport(importedPolicyId: string) {

  dom.crudImport.idValue = importedPolicyId;
  dom.tbodyPolicyImportEntries.textContent = '';
  loadTransitiveImportsFromImport(importedPolicyId);

  if (importedPolicyId) {
    const importedPolicy: Policies.Policy = await API.callDittoREST('GET', '/policies/' + importedPolicyId)
    Object.keys(importedPolicy.entries).forEach((entry) => {
      const row = dom.tbodyPolicyImportEntries.insertRow();
      const checkbox: HTMLInputElement = Utils.addCheckboxToRow(row, entry, isImportedOrImplicit(entry, importedPolicy), true);
      checkbox.dataset.importable = importedPolicy.entries[entry].importable;
      Utils.addCellToRow(row, entry, JSON.stringify(importedPolicy.entries[entry], null, 4));
      Utils.addCellToRow(row, importedPolicy.entries[entry].importable);
    });
  }

  function isImportedOrImplicit(entry: string, importedPolicy: Policies.Policy): boolean {
    const policyImport = Policies.thePolicy.imports[importedPolicyId];
    const isImported = policyImport && policyImport['entries'] && policyImport.entries.includes(entry);
    const isImplicit = importedPolicy.entries[entry].importable === 'implicit';
    return isImported || isImplicit;
  }
}

function loadTransitiveImportsFromImport(importedPolicyId: string) {
  dom.tbodyPolicyTransitiveImports.textContent = '';
  dom.inputTransitiveImport.value = '';
  // Two cases where there's no persisted import body: no selection yet, or the user is creating a
  // brand-new import (typing the id, not yet PUT). In the latter case we still want the controls to
  // follow the edit-mode flag so the user can author transitive imports as part of the new entry.
  const importBody = importedPolicyId ? Policies.thePolicy?.imports[importedPolicyId] : undefined;
  (importBody?.transitiveImports ?? [])
      .forEach((id) => appendTransitiveImportRow(id, !dom.crudImport.isEditing));
  setTransitiveImportsControlsDisabled(!dom.crudImport.isEditing);
}




