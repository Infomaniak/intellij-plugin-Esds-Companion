# Changelog

## 2.0.0

- Renamed to **Esds Companion**. The plugin ID changed to `com.infomaniak.esdscompanion`, so
  uninstall the previous "Infomaniak Design System Token Info" plugin before installing this one.
  The inlay hints setting also resets to off, because it is keyed by the renamed provider.
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
