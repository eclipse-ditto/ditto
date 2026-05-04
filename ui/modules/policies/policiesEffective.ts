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

import * as ace from 'ace-builds/src-noconflict/ace';
import { Ace } from 'ace-builds';
import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Policies from './policies.js';

type DomElements = {
  buttonRefreshEffectivePolicy: HTMLButtonElement,
}

let dom: DomElements = {
  buttonRefreshEffectivePolicy: null,
};

let effectivePolicyEditor: Ace.Editor;
let lastLoadedPolicyId: string | null = null;

const PLACEHOLDER_NO_POLICY = '// Load a policy to see its effective (resolved) view.';

export function ready() {
  Utils.getAllElementsById(dom);
  Policies.observable.addChangeListener(onPolicyChanged);

  effectivePolicyEditor = Utils.createAceEditor('effectivePolicyEditor', 'ace/mode/json', true);
  effectivePolicyEditor.setReadOnly(true);
  effectivePolicyEditor.renderer.setShowGutter(false);
  effectivePolicyEditor.setValue(PLACEHOLDER_NO_POLICY, -1);

  dom.buttonRefreshEffectivePolicy.addEventListener('click', () => {
    if (lastLoadedPolicyId) {
      loadEffectivePolicy(lastLoadedPolicyId);
    }
  });

  // Re-fetch only when the user activates the tab — avoids an extra request on every policy load.
  document.querySelector('a[data-bs-target="#tabPolicyEffective"]').addEventListener('shown.bs.tab', () => {
    // Ace caches its measured container size from creation time. The editor is created while the
    // tab-pane is display:none, so Ace measured 0x0 and stuck with it. Re-measure on every tab show.
    effectivePolicyEditor.resize(true);
    effectivePolicyEditor.renderer.updateFull(true);
    if (Policies.thePolicy && Policies.thePolicy.policyId !== lastLoadedPolicyId) {
      loadEffectivePolicy(Policies.thePolicy.policyId);
    }
  });
}

function onPolicyChanged(policy: Policies.Policy) {
  if (!policy) {
    lastLoadedPolicyId = null;
    effectivePolicyEditor.setValue(PLACEHOLDER_NO_POLICY, -1);
    return;
  }
  if (lastLoadedPolicyId === policy.policyId) {
    return; // already showing the right thing
  }
  lastLoadedPolicyId = null;
  // If the user is currently looking at the Effective tab, fetch immediately — switching the policy
  // from the Recent list while on this tab does NOT fire shown.bs.tab, so we'd otherwise be stuck
  // showing stale content. Off-tab, just invalidate; the tab activation handler will load on demand.
  if (isEffectiveTabActive()) {
    loadEffectivePolicy(policy.policyId);
  } else {
    effectivePolicyEditor.setValue('', -1);
  }
}

function isEffectiveTabActive(): boolean {
  const tabAnchor = document.querySelector('a[data-bs-target="#tabPolicyEffective"]');
  return tabAnchor?.classList.contains('active') ?? false;
}

async function loadEffectivePolicy(policyId: string) {
  effectivePolicyEditor.setValue('// loading…', -1);
  try {
    // policy-view=resolved is the gateway-side flag introduced by the effective-policy-view feature; on
    // older deployments that don't recognise the parameter the gateway returns the original (stored) view,
    // which is a graceful fallback.
    const resolved = await API.callDittoREST('GET',
      `/policies/${encodeURIComponent(policyId)}?policy-view=resolved`);
    effectivePolicyEditor.setValue(JSON.stringify(resolved, null, 2), -1);
    lastLoadedPolicyId = policyId;
  } catch (e) {
    effectivePolicyEditor.setValue(`// could not load effective view: ${formatError(e)}`, -1);
    lastLoadedPolicyId = null;
  }
  // Force a full re-render: the editor was likely hidden when setValue was called (tab-pane hidden until
  // shown), and Ace doesn't auto-relayout on visibility changes — without this the canvas paints nothing.
  effectivePolicyEditor.resize(true);
  effectivePolicyEditor.renderer.updateFull(true);
}

function formatError(e: unknown): string {
  if (e instanceof Error) {
    return e.message;
  }
  if (typeof e === 'string') {
    return e;
  }
  return JSON.stringify(e);
}
