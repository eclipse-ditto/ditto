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

const LOCAL_OPTION_VALUE = '__local__';
const LOCAL_OPTION_LABEL = '(local)';

let selectedReferenceIndex: number | null = null;

type DomElements = {
  tbodyPolicyReferences: HTMLTableElement,
  tableValidationReferences: HTMLInputElement,
  crudReference: CrudToolbar,
  selectReferenceImport: HTMLSelectElement,
  selectReferenceEntry: HTMLSelectElement,
  inputReferenceEntryFallback: HTMLInputElement,
  referenceWarning: HTMLElement,
}

let dom: DomElements = {
  tbodyPolicyReferences: null,
  tableValidationReferences: null,
  crudReference: null,
  selectReferenceImport: null,
  selectReferenceEntry: null,
  inputReferenceEntryFallback: null,
  referenceWarning: null,
};

// Cache imported policies fetched for label-lookup. Keyed by the policyId of the imported policy.
// The cached value is the in-flight or resolved Promise; failures are NOT cached so the user can retry.
// Cleared whenever the loaded policy changes (see onPolicyChanged).
let importedPolicyCache: Map<string, Promise<Policies.Policy>> = new Map();
let cacheOwnerPolicyId: string | null = null;

export function ready() {
  Utils.getAllElementsById(dom);
  Policies.observable.addChangeListener(onPolicyChanged);
  PolicyEntries.observable.addChangeListener(onEntryChanged);

  Utils.addValidatorToTable(dom.tbodyPolicyReferences, dom.tableValidationReferences);

  dom.crudReference.editDisabled = true;
  dom.crudReference.addEventListener('onCreateClick', onCreateReferenceClick);
  dom.crudReference.addEventListener('onUpdateClick', onUpdateReferenceClick);
  dom.crudReference.addEventListener('onDeleteClick', onDeleteReferenceClick);
  dom.crudReference.addEventListener('onEditToggle', onEditToggleReference);

  dom.selectReferenceImport.addEventListener('change', onImportSelectChange);
  dom.tbodyPolicyReferences.onclick = onPolicyReferencesClick;
}

function onPolicyChanged(policy: Policies.Policy) {
  // The cache is per-loaded-policy: an imported policy fetched while editing policy A is irrelevant
  // (and possibly stale) when the user switches to policy B. Cheaper to drop the cache than to
  // reason about whether it's still valid.
  if (!policy || policy.policyId !== cacheOwnerPolicyId) {
    importedPolicyCache = new Map();
    cacheOwnerPolicyId = policy ? policy.policyId : null;
  }
}

function onEntryChanged(entryLabel: string) {
  selectedReferenceIndex = null;
  dom.tbodyPolicyReferences.textContent = '';
  dom.referenceWarning.textContent = '';
  dom.crudReference.idValue = null;
  dom.crudReference.editDisabled = (entryLabel === null);

  populateImportSelect();
  resetEntrySelect();

  if (Policies.thePolicy && entryLabel) {
    // Pre-populate the Entry dropdown for the default (local) Import value so the user can pick a
    // local entry without first interacting with the Import dropdown — selecting the default value
    // does not fire a change event, so onImportSelectChange would never run otherwise.
    loadEntriesForImport(LOCAL_OPTION_VALUE);

    const refs = Policies.thePolicy.entries[entryLabel].references ?? [];
    refs.forEach((ref, idx) => {
      const importLabel = ref.import ?? LOCAL_OPTION_LABEL;
      const row = Utils.addTableRow(dom.tbodyPolicyReferences, displayKeyForRef(idx, ref), false,
          null, importLabel, ref.entry);
      row.dataset.refIndex = String(idx);
    });
  }
}

function displayKeyForRef(index: number, ref: Policies.EntryReference): string {
  // Synthetic key — used as the row id and the crud-toolbar idValue. Index makes it unique even when
  // two references differ only by import.
  const importPart = ref.import ?? LOCAL_OPTION_LABEL;
  return `#${index} ${importPart} → ${ref.entry}`;
}

function populateImportSelect() {
  Utils.setOptions(dom.selectReferenceImport, []);
  const localOption = new Option(LOCAL_OPTION_LABEL, LOCAL_OPTION_VALUE);
  dom.selectReferenceImport.add(localOption);
  if (Policies.thePolicy) {
    Object.keys(Policies.thePolicy.imports).forEach((id) => {
      dom.selectReferenceImport.add(new Option(id, id));
    });
  }
  dom.selectReferenceImport.value = LOCAL_OPTION_VALUE;
}

function resetEntrySelect() {
  Utils.setOptions(dom.selectReferenceEntry, []);
  dom.inputReferenceEntryFallback.hidden = true;
  dom.selectReferenceEntry.hidden = false;
}

async function onImportSelectChange() {
  await loadEntriesForImport(dom.selectReferenceImport.value);
}

async function loadEntriesForImport(importValue: string, preselectEntry?: string) {
  resetEntrySelect();
  dom.referenceWarning.textContent = '';
  if (importValue === LOCAL_OPTION_VALUE) {
    const labels = Policies.thePolicy ? Object.keys(Policies.thePolicy.entries) : [];
    populateEntrySelect(labels, preselectEntry);
    if (preselectEntry && !labels.includes(preselectEntry)) {
      dom.referenceWarning.textContent = `Local entry "${preselectEntry}" not found in this policy.`;
    }
    return;
  }
  // Imported policy lookup — show "Loading…" while we fetch (or hit the cache).
  populateEntrySelect([], undefined);
  dom.selectReferenceEntry.add(new Option('Loading…', '', true, true));
  dom.selectReferenceEntry.disabled = true;
  try {
    const importedPolicy = await getImportedPolicy(importValue);
    const labels = Object.keys(importedPolicy.entries);
    populateEntrySelect(labels, preselectEntry);
    if (preselectEntry && !labels.includes(preselectEntry)) {
      dom.referenceWarning.textContent =
          `Entry "${preselectEntry}" not found in imported policy "${importValue}".`;
    }
  } catch (e) {
    // Fetch failed — fall back to free-text input so the user can still author/keep the reference.
    dom.selectReferenceEntry.hidden = true;
    dom.inputReferenceEntryFallback.hidden = false;
    dom.inputReferenceEntryFallback.disabled = !dom.crudReference.isEditing;
    dom.inputReferenceEntryFallback.value = preselectEntry ?? '';
    dom.referenceWarning.textContent =
        `Could not load imported policy "${importValue}": ${formatError(e)}`;
  } finally {
    dom.selectReferenceEntry.disabled = !dom.crudReference.isEditing;
  }
}

function populateEntrySelect(labels: string[], preselectEntry: string | undefined) {
  Utils.setOptions(dom.selectReferenceEntry, labels);
  dom.selectReferenceEntry.hidden = false;
  dom.inputReferenceEntryFallback.hidden = true;
  if (preselectEntry !== undefined) {
    if (labels.includes(preselectEntry)) {
      dom.selectReferenceEntry.value = preselectEntry;
    } else {
      // Add a placeholder option so the user sees the dangling label rather than silently losing it.
      const opt = new Option(preselectEntry + ' (missing)', preselectEntry, true, true);
      dom.selectReferenceEntry.add(opt);
    }
  }
}

function getImportedPolicy(policyId: string): Promise<Policies.Policy> {
  if (importedPolicyCache.has(policyId)) {
    return importedPolicyCache.get(policyId);
  }
  const promise = API.callDittoREST('GET', '/policies/' + policyId) as Promise<Policies.Policy>;
  importedPolicyCache.set(policyId, promise);
  // Don't cache failures — let the user retry on the next dropdown change.
  promise.catch(() => importedPolicyCache.delete(policyId));
  return promise;
}

function formatError(e: unknown): string {
  if (e instanceof Error) return e.message;
  if (typeof e === 'string') return e;
  return JSON.stringify(e);
}

function onPolicyReferencesClick(event: MouseEvent) {
  const target = event.target as HTMLElement;
  const row = target.closest('tr');
  if (!row || !row.dataset.refIndex) {
    return;
  }
  const idx = Number(row.dataset.refIndex);
  if (selectedReferenceIndex === idx) {
    selectedReferenceIndex = null;
    dom.crudReference.idValue = null;
    resetEntrySelect();
    populateImportSelect();
    return;
  }
  selectedReferenceIndex = idx;
  const ref = Policies.thePolicy.entries[PolicyEntries.selectedEntry].references[idx];
  dom.crudReference.idValue = displayKeyForRef(idx, ref);
  populateImportSelect();
  dom.selectReferenceImport.value = ref.import ?? LOCAL_OPTION_VALUE;
  loadEntriesForImport(dom.selectReferenceImport.value, ref.entry);
}

function buildReferenceFromUI(): Policies.EntryReference {
  const importValue = dom.selectReferenceImport.value;
  const entryLabel = dom.inputReferenceEntryFallback.hidden
      ? dom.selectReferenceEntry.value
      : dom.inputReferenceEntryFallback.value.trim();
  Utils.assert(entryLabel, 'Please pick or type an entry label', dom.crudReference.validationElement);
  const ref: Policies.EntryReference = { entry: entryLabel };
  if (importValue !== LOCAL_OPTION_VALUE) {
    ref.import = importValue;
  }
  return ref;
}

function onCreateReferenceClick() {
  PolicyEntries.validateSelected();
  const ref = buildReferenceFromUI();
  const entry = Policies.thePolicy.entries[PolicyEntries.selectedEntry];
  const next = [...(entry.references ?? []), ref];
  putEntryWithReferences(next, Policies.finishEditing(dom.crudReference, CrudOperation.CREATE));
}

function onUpdateReferenceClick() {
  PolicyEntries.validateSelected();
  Utils.assert(selectedReferenceIndex !== null, 'Please select a reference',
      dom.tableValidationReferences);
  const ref = buildReferenceFromUI();
  const entry = Policies.thePolicy.entries[PolicyEntries.selectedEntry];
  const next = [...(entry.references ?? [])];
  next[selectedReferenceIndex] = ref;
  putEntryWithReferences(next, Policies.finishEditing(dom.crudReference, CrudOperation.UPDATE));
}

function onDeleteReferenceClick() {
  PolicyEntries.validateSelected();
  Utils.assert(selectedReferenceIndex !== null, 'Please select a reference',
      dom.tableValidationReferences);
  const entry = Policies.thePolicy.entries[PolicyEntries.selectedEntry];
  const next = (entry.references ?? []).filter((_, i) => i !== selectedReferenceIndex);
  putEntryWithReferences(next, Policies.finishEditing(dom.crudReference, CrudOperation.DELETE));
}

// Always read the latest entry body at PUT time. Empty list collapses to "field absent" so
// previously-untouched entries stay untouched.
function putEntryWithReferences(refs: Policies.EntryReference[], onSuccess: (value: any) => any) {
  const entry = { ...Policies.thePolicy.entries[PolicyEntries.selectedEntry] };
  if (refs.length === 0) {
    delete entry.references;
  } else {
    entry.references = refs;
  }
  API.callDittoREST('PUT',
      `/policies/${Policies.thePolicy.policyId}/entries/${PolicyEntries.selectedEntry}`,
      entry
  ).then(onSuccess);
}

function onEditToggleReference(event: CustomEvent) {
  const editing: boolean = event.detail.isEditing;
  dom.selectReferenceImport.disabled = !editing;
  dom.selectReferenceEntry.disabled = !editing;
  dom.inputReferenceEntryFallback.disabled = !editing;
  // The crud-toolbar's idValue input is a read-only display for references — the actual data comes
  // from the two selects below. CrudToolbar would normally enable it during create (empty value);
  // override that to avoid suggesting the synthetic display key is an authored field.
  dom.crudReference.validationElement.disabled = true;
  if (event.detail.isCancel) {
    onEntryChanged(PolicyEntries.selectedEntry);
  }
}
