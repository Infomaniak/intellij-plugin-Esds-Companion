# Changelog

## 2.4.0

- New inlay hint showing the dp size of a sized icon: `EsdsIcons.Plus.Small` displays `20dp`. It works
  on any `Symbol`, such as `icon.Large` with `icon: Symbol`. On by default, under *Settings | Editor |
  Inlay Hints | Values*.

## 2.3.0

- Follow the design system's new icon API, `EsdsIcons.Plus.Medium`. Completing `EsdsIcons.` previews
  each icon at its default size; completing `EsdsIcons.Plus.` previews each size next to its dp size.
- The gutter previews `EsdsIcons.Plus` and `EsdsIcons.Plus.current` at the default `Medium` size.

## 2.2.0

- Icon previews: `EsdsIcons.Medium.Plus` is previewed in code completion and in the gutter. Icons are
  read from the design system the project depends on, so they always match its version.
- Completing `EsdsIcons.` shows the size each category is designed for (`Medium` is `24dp`).
- New *Settings | Tools | Esds Companion* page to pick the preview color. Previews follow the editor
  text color by default.

## 2.1.0

- Support `EsdsTheme.opacity` tokens. Values are shown as percentages: `EsdsTheme.opacity.ghost`
  displays `5%` (the `0.05f` alpha). Value completion works too: `EsdsTheme.opacity.5` lists
  `ghost 5%` and `medium 50%`.

## 2.0.0

- Renamed to **Esds Companion**. The plugin ID changed to `com.infomaniak.esdscompanion`, so
  uninstall the previous "Infomaniak Design System Token Info" plugin before installing this one.
- Inlay hints are now on by default. They can still be turned off under
  *Settings | Editor | Inlay Hints | Values*.
- Released publicly under GPL-3.0.

## 1.1.0

- Find a token by its value: typing `EsdsTheme.spacing.1` lists the spacings whose value starts with
  1 (`lg 12dp`, `xl 16dp`, `eightXl 100dp`), and `EsdsTheme.spacing.16` narrows it down to `xl`.
  Picking an item replaces the number with the token name. Works for every token category.

## 1.0.1

- Fix clicking an inlay hint doing nothing when the design system comes from a library: the target
  file lives inside a jar, which the previous `file://` lookup could not reach.
- Clicking a hint now reports why it cannot navigate instead of staying silent.

## 1.0.0

- Show the resolved value of Infomaniak design system tokens in Kotlin code completion:
  `EsdsTheme.icon.sizeSm` now displays `20dp` on the right of the completion item.
- Optional inlay hints show the same values inline at each call site. They are off by default and
  can be switched on per user under *Settings | Editor | Inlay Hints | Values*.
- Ctrl-click (Cmd-click on macOS) an inlay hint to jump to the design system source that defines the
  token, for instance `DefaultIconTokens.kt` on the `sizeSm = ...` line.
- Supported token groups: `EsdsTheme.icon`, `EsdsTheme.spacing`, `EsdsTheme.radius`.
- Also decorates per-app theme wrappers (`MailTheme.spacing.md`).
