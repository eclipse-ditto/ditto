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
import AceDiff from 'ace-diff';
import * as Utils from '../utils.js';
import subDiffHTML from './subDiff.html';

/**
 * Lightweight reusable diff panel without revision controls.
 * Shows a side-by-side ace-diff viewer in a dynamically added tab.
 * Content is provided externally via update().
 */
export function SubDiff(targetTab, title: string) {
  let diffInstance: AceDiff | null = null;
  let tabLink: HTMLAnchorElement | null = null;
  let container: HTMLDivElement | null = null;
  let pendingLeft: string | null = null;
  let pendingRight: string | null = null;
  let onActivatedCallback: ((link: HTMLAnchorElement) => void) | null = null;
  let scrollSyncing = false;

  return { ready, update, getTabLink, setOnActivated, destroy: destroyDiff };

  async function ready() {
    const tabId = Utils.addTab(
      document.getElementById(targetTab.itemsId),
      document.getElementById(targetTab.contentId),
      '<i class="bi bi-file-diff"></i> Diff',
      subDiffHTML,
      title
    );

    tabLink = document.querySelector(`a[data-bs-target="#${tabId}"]`);
    container = document.querySelector(`#${tabId} .sub-diff-container`);
    tabLink.addEventListener('shown.bs.tab', onTabActivated);
  }

  function update(leftContent: string, rightContent: string) {
    pendingLeft = leftContent;
    pendingRight = rightContent;
    if (tabLink?.classList.contains('active')) {
      applyDiff();
    }
  }

  function getTabLink(): HTMLAnchorElement | null {
    return tabLink;
  }

  function setOnActivated(callback: (link: HTMLAnchorElement) => void) {
    onActivatedCallback = callback;
  }

  function onTabActivated() {
    if (pendingLeft !== null) {
      applyDiff();
    }
    if (onActivatedCallback && tabLink) {
      onActivatedCallback(tabLink);
    }
  }

  function applyDiff() {
    if (!container) return;
    const left = pendingLeft || '';
    const right = pendingRight || '';

    if (diffInstance) {
      const editors = diffInstance.getEditors();
      scrollSyncing = true;
      editors.left.setValue(left, -1);
      editors.right.setValue(right, -1);
      editors.left.getSession().setScrollTop(0);
      editors.right.getSession().setScrollTop(0);
      scrollSyncing = false;
      diffInstance.diff();
    } else {
      diffInstance = new AceDiff({
        ace: ace as unknown as { edit: typeof ace.edit },
        element: container,
        mode: 'ace/mode/json',
        theme: null,
        diffGranularity: 'specific',
        lockScrolling: false,
        showDiffs: true,
        showConnectors: true,
        charDiffs: true,
        maxDiffs: 5000,
        left: {
          content: left,
          editable: false,
          copyLinkEnabled: false,
        },
        right: {
          content: right,
          editable: false,
          copyLinkEnabled: false,
        },
      });
      setupScrollSync();
    }
  }

  function setupScrollSync() {
    if (!diffInstance) return;
    const editors = diffInstance.getEditors();

    const sync = (source, target) => {
      if (scrollSyncing) return;
      scrollSyncing = true;

      const srcSession = source.getSession();
      const tgtSession = target.getSession();
      const srcTop = srcSession.getScrollTop();
      const lineHeight = source.renderer.lineHeight || 16;
      const srcTotal = srcSession.getLength() * lineHeight;
      const srcVisible = source.renderer.$size.scrollerHeight;
      const srcMax = Math.max(0, srcTotal - srcVisible);
      const ratio = srcMax > 0 ? srcTop / srcMax : 0;

      const tgtTotal = tgtSession.getLength() * lineHeight;
      const tgtVisible = target.renderer.$size.scrollerHeight;
      const tgtMax = Math.max(0, tgtTotal - tgtVisible);

      tgtSession.setScrollTop(Math.round(ratio * tgtMax));
      scrollSyncing = false;
    };

    editors.left.getSession().on('changeScrollTop', () => sync(editors.left, editors.right));
    editors.right.getSession().on('changeScrollTop', () => sync(editors.right, editors.left));
  }

  function destroyDiff() {
    if (diffInstance) {
      diffInstance.destroy();
      diffInstance = null;
    }
    scrollSyncing = false;
    pendingLeft = null;
    pendingRight = null;
    if (container) {
      container.textContent = '';
    }
  }
}
