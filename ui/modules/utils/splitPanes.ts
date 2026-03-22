/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
import Split from "split.js";

/**
 * Initializes all split panes in the document.
 * Elements with data-split="horizontal" become resizable split
 * containers. Their direct children with class "split-panel"
 * are the resizable panels.
 */
document.addEventListener("mouseup", () => {
  document.body.classList.remove("split-dragging-h", "split-dragging-v");
});

export function initSplitPanes(): void {
  document.querySelectorAll<HTMLElement>("[data-split]").forEach((container) => {
    const direction = container.dataset.split as "horizontal" | "vertical";
    const sizesAttr = container.dataset.splitSizes;
    const minAttr = container.dataset.splitMin;
    const storageKey = container.dataset.splitKey;

    const panels = Array.from(container.children).filter((child) =>
      child.classList.contains("split-panel"),
    ) as HTMLElement[];

    if (panels.length < 2) return;

    let sizes: number[] | undefined;
    if (storageKey) {
      const saved = localStorage.getItem(`split-sizes-${storageKey}`);
      if (saved) {
        try {
          sizes = JSON.parse(saved);
        } catch {
          /* ignore corrupt data */
        }
      }
    }
    if (!sizes && sizesAttr) {
      try {
        sizes = JSON.parse(sizesAttr);
      } catch {
        /* ignore */
      }
    }
    if (!sizes) {
      sizes = panels.map(() => 100 / panels.length);
    }

    Split(panels, {
      sizes,
      minSize: minAttr ? JSON.parse(minAttr) : 50,
      gutterSize: 6,
      direction,
      snapOffset: 0,
      elementStyle: (_dimension, size, gutterSize) => ({
        "flex-basis": `calc(${size}% - ${gutterSize}px)`,
      }),
      gutterStyle: (_dimension, gutterSize) => ({
        "flex-basis": `${gutterSize}px`,
      }),
      onDragStart: () => {
        document.body.classList.add(direction === "horizontal" ? "split-dragging-h" : "split-dragging-v");
      },
      onDragEnd: (newSizes) => {
        document.body.classList.remove("split-dragging-h", "split-dragging-v");
        if (storageKey) {
          localStorage.setItem(`split-sizes-${storageKey}`, JSON.stringify(newSizes));
        }
      },
      onDrag: () => {
        window.dispatchEvent(new Event("resize"));
      },
    });
  });
}
