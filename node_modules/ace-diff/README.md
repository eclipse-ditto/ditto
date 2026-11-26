# Ace-diff

This is a wrapper for [Ace Editor](http://ace.c9.io/) to provide a 2-panel diffing/merging tool that visualizes differences in two documents and allows users to copy changes from to the other.

![Ace-diff demo](https://ace-diff.github.io/ace-diff/demos/screenshot1.png)

It's built on top of [@sanity/diff-match-patch](https://github.com/sanity-io/diff-match-patch) library. That lib handles the hard part: the computation of the document diffs. Ace-diff just visualizes that information as line-diffs in the editors.

## Dependencies

- Ace Editor: this could the [official Ace builds](https://github.com/ajaxorg/ace-builds), [Brace](https://github.com/thlorenz/brace) and any other similar Ace editor build (like the ones from public CDNs)

## Demos

Take a look at [demos on Ace-diff page](https://ace-diff.github.io/ace-diff/). The demos illustrate a few different configurations and styles. Hopefully they'll give you a rough sense of what it does and how it works.

## Features

- Compatible with any Ace/Brace Editor mode or theme
- Accommodates realtime changes to one or both editors
- Readonly option for left/right editors
- Control how aggressively diffs are combined
- Allow users to copy diffs from one side to the other
- Character-level diff highlighting (shows exact changes within lines)
- Gutter decorations marking changed lines

## How to install

```bash
pnpm add ace-diff
pnpm add ace-builds

# or with npm
npm i ace-diff -S
npm i ace-builds -S

# or with yarn
yarn add ace-diff
yarn add ace-builds
```

```js
import AceDiff from 'ace-diff'
import * as ace from 'ace-builds'

// optionally, include CSS, or use your own
import 'ace-diff/styles.css'
// or import 'ace-diff/styles' (same file)
```

### Upgrading from v3 to v4

1. Update your CSS import:

   ```js
   // Before (v3.x)
   import 'ace-diff/dist/ace-diff.min.css'

   // After (v4.x)
   import 'ace-diff/styles.css'
   ```

2. If you use `ace-builds`, ensure it's installed as a dependency:

   ```bash
   pnpm add ace-builds
   ```

   Note: ace-diff works with any Ace-compatible editor (Brace, CDN builds, etc.), so `ace-builds` is not strictly required.

3. TypeScript users get types automatically - no need for `@types/ace-diff`

### HTML

```html
<div class="acediff"></div>
```

### JavaScript

Here's an example of how you'd instantiate AceDiff.

```js
const differ = new AceDiff({
  ace: window.ace, // You Ace Editor instance
  element: '.acediff',
  left: {
    content: 'your first file content here',
  },
  right: {
    content: 'your second file content here',
  },
})
```

When using with Brace or any build system like Webpack, you can pass the editor as an option:

```js
// If you are using Brace editor, you can pass it as well
const ace = require('brace')

const differ = new AceDiff({
  ace, // using Brace
  element: '.acediff',
  left: {
    content: 'your first file content here',
  },
  right: {
    content: 'your second file content here',
  },
})
```

### CSS

**Because of the way how ACE is positioned, it's important to have Ace-diff running in some container with specified dimensions (and optionally with a `position: relative`)**

Styling the elements is vitally important: the gutter should retain its width even if the user resizes his or her browser. But honestly, how you go about that is very much up to you: you can provide whatever CSS you want, depending on your scenario.

If you want the ace editor's to change height/width based on a user's browser, I find using flexbox the best option - but hell, if you want to use a `<table>`, knock yourself out. :)

Take a look at the [demos](https://ace-diff.github.io/ace-diff/) for some ideas. They all use flexbox for the layouts, but include some different styles and class names just so you can see.

## Theming

Ace-diff uses CSS custom properties (variables) for colors, making it easy to match your Ace editor theme. The default styles use a light theme.

### CSS Custom Properties

Override these variables to customize the appearance:

```css
.acediff {
  --acediff-gutter-bg: #efefef; /* Gutter background */
  --acediff-gutter-border: #bcbcbc; /* Gutter border color */
  --acediff-diff-bg: #d8f2ff; /* Diff highlight background */
  --acediff-diff-border: #a2d7f2; /* Diff highlight border */
  --acediff-arrow-color: #000; /* Copy arrow color */
  --acediff-arrow-shadow: rgba(255, 255, 255, 0.7); /* Arrow text shadow */
  --acediff-arrow-hover-left: #004ea0; /* Left arrow hover color */
  --acediff-arrow-hover-right: #c98100; /* Right arrow hover color */
}
```

### Theme Presets

We provide a preset for the Twilight Ace theme. Import it after the base CSS:

```js
import 'ace-diff/styles.css'
import 'ace-diff/styles-twilight.css'
```

Then set the Ace editor theme to match:

```js
const differ = new AceDiff({
  theme: 'ace/theme/twilight',
  // ... other options
})
```

### Creating Custom Themes

To create your own theme preset, simply override the CSS variables:

```css
/* My custom dark theme */
.acediff {
  --acediff-gutter-bg: #1e1e1e;
  --acediff-gutter-border: #333;
  --acediff-diff-bg: #264f78;
  --acediff-diff-border: #1e3a5f;
  --acediff-arrow-color: #fff;
  --acediff-arrow-shadow: rgba(0, 0, 0, 0.7);
  --acediff-arrow-hover-left: #569cd6;
  --acediff-arrow-hover-right: #ce9178;
}
```

## Configuration

You can configure your Ace-diff instance through a number of config settings. This object is what you pass to the constructor, like the **JavaScript** section above.

### Default settings

Here are all the defaults. I'll explain each one in details below. Note: you only need to override whatever you want.

```javascript
{
  ace: window.ace,
  mode: null,
  theme: null,
  element: null,
  diffGranularity: 'broad',
  lockScrolling: true,
  showDiffs: true,
  showConnectors: true,
  charDiffs: true,
  maxDiffs: 5000,
  left: {
    id: null,
    content: null,
    mode: null,
    theme: null,
    editable: true,
    copyLinkEnabled: true
  },
  right: {
    id: null,
    content: null,
    mode: null,
    theme: null,
    editable: true,
    copyLinkEnabled: true
  },
  classes: {
    gutterID: 'acediff__gutter',
    diff: 'acediff__diffLine',
    diffChar: 'acediff__diffChar',
    diffGutter: 'acediff__diffGutter',
    connector: 'acediff__connector',
    newCodeConnectorLink: 'acediff__newCodeConnector',
    newCodeConnectorLinkContent: '&#8594;',
    deletedCodeConnectorLink: 'acediff__deletedCodeConnector',
    deletedCodeConnectorLinkContent: '&#8592;',
    copyRightContainer: 'acediff__copy--right',
    copyLeftContainer: 'acediff__copy--left',
  },
  connectorYOffset: 0,
  onDiffReady: null,
}
```

### Diffing settings

- `ace` (object, optional, default: `window.ace`). The Ace Editor instance to use.
- `element` (string<DOM selector> or element object, required). The element used for Ace-diff
- `mode` (string, optional). this is the mode for the Ace Editor, e.g. `"ace/mode/javascript"`. Check out the Ace docs for that. This setting will be applied to both editors. I figured 99.999999% of the time you're going to want the same mode for both of them so you can just set it once here. If you're a mad genius and want to have different modes for each side, (a) _whoah man, what's your use-case?_, and (b) you can override this setting in one of the settings below. Read on.
- `theme` (string, optional). This lets you set the theme for both editors.
- `diffGranularity` (string, optional, default: `broad`). this has two options (`specific`, and `broad`). Basically this determines how aggressively AceDiff combines diffs to simplify the interface. I found that often it's a judgement call as to whether multiple diffs on one side should be grouped. This setting provides a little control over it.
- `lockScrolling` (boolean, optional, default: `true`). Synchronizes scrolling between the left and right editors. When enabled, scrolling one editor will scroll the other proportionally. Set to `false` to allow independent scrolling.
- `showDiffs` (boolean, optional, default: `true`). Whether or not the diffs are enabled. This basically turns everything off.
- `showConnectors` (boolean, optional, default: `true`). Whether or not the gutter in the middle show show connectors visualizing where the left and right changes map to one another.
- `charDiffs` (boolean, optional, default: `true`). When enabled, highlights the specific characters that changed within a line, not just the whole line. Provides more granular diff visualization.
- `maxDiffs` (integer, optional, default: `5000`). This was added a safety precaution. For really massive files with vast numbers of diffs, it's possible the Ace instances or AceDiff will become too laggy. This simply disables the diffing altogether once you hit a certain number of diffs.
- `left/right`. this object contains settings specific to the leftmost editor.
- `left.content / right.content` (string, optional, default: `null`). If you like, when you instantiate AceDiff you can include the content that should appear in the leftmost editor via this property.
- `left.mode / right.mode` (string, optional, defaults to whatever you entered in `mode`). This lets you override the default Ace Editor mode specified in `mode`.
- `left.theme / right.theme` (string, optional, defaults to whatever you entered in `theme`). This lets you override the default Ace Editor theme specified in `theme`.
- `left.editable / right.editable` (boolean, optional, default: `true`). Whether the left editor is editable or not.
- `left.copyLinkEnabled / right.copyLinkEnabled` (boolean, optional, default: `true`). Whether the copy to right/left arrows should appear.
- `connectorYOffset` (integer, optional, default: `0`). Vertical offset for connector lines in the gutter.
- `onDiffReady` (function, optional, default: `null`). Callback function invoked after diffs are computed and displayed. Receives the diffs array as its argument. Useful for scrolling to the first diff or implementing diff navigation.

### Classes

- `gutterID`: the ID for the gutter element between editors
- `diff`: the class for a diff line on either editor
- `diffChar`: the class for character-level diff highlighting (used when `charDiffs` is enabled)
- `diffGutter`: the class for gutter decorations on diff lines
- `connector`: the SVG connector class
- `newCodeConnectorLink`: the class for the copy-to-right link element
- `newCodeConnectorLinkContent`: the content of the copy to right link. Defaults to a unicode right arrow ('&#8594;')
- `deletedCodeConnectorLink`: the class for the copy-to-left link element
- `deletedCodeConnectorLinkContent`: the content of the copy to left link. Defaults to a unicode left arrow ('&#8592;')
- `copyRightContainer`: the class for the container holding copy-to-right arrows
- `copyLeftContainer`: the class for the container holding copy-to-left arrows

## API

There are a few API methods available on your AceDiff instance.

- `aceInstance.getEditors()`: this returns an object with left and right properties. Each contains a reference to the Ace editor, in case you need to do anything with them. Ace has a ton of options which I wasn't going to support via the wrapper. This should allow you to do whatever you need
- `aceInstance.setOptions()`: this lets you set many of the above options on the fly. Note: certain things used during the construction of the editor, like the classes can't be overridden.
- `aceInstance.getNumDiffs()`: returns the number of diffs currently being displayed.
- `aceInstance.diff()`: updates the diff. This shouldn't ever be required because AceDiff automatically recognizes the key events like changes to the editor and window resizing. But I've included it because there may always be that fringe case...
- `aceInstance.clear()`: clears all diff markers, gutter decorations, and connectors without destroying the editors. Useful when you want to temporarily hide diffs.
- `aceInstance.destroy()`: destroys the AceDiff instance. Basically this just destroys both editors and cleans out the gutter.

## Browser Support

All modern browsers. Open a ticket if you find otherwise.

## COSMOS

Ace-diff is in active use in the [COSMOS](https://github.com/openc3/cosmos) project and is maintained by the OpenC3 team. See the [PluginDialog](https://github.com/OpenC3/cosmos/blob/ec5be5bb237fe397eb97ed2f026eaf7affd8b02f/openc3-cosmos-init/plugins/packages/openc3-vue-common/src/tools/admin/PluginDialog.vue) for a full example.

## License

MIT.
