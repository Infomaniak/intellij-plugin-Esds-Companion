# Changelog

## 1.0.0

- Show the resolved value of Infomaniak design system tokens in Kotlin code completion:
  `EsdsTheme.icon.sizeSm` now displays `20dp` on the right of the completion item.
- Optional inlay hints show the same values inline at each call site. They are off by default and
  can be switched on per user under *Settings | Editor | Inlay Hints | Values*.
- Ctrl-click (Cmd-click on macOS) an inlay hint to jump to the design system source that defines the
  token, for instance `DefaultIconTokens.kt` on the `sizeSm = ...` line.
- Supported token groups: `EsdsTheme.icon`, `EsdsTheme.spacing`, `EsdsTheme.radius`.
- Also decorates per-app theme wrappers (`MailTheme.spacing.md`).
