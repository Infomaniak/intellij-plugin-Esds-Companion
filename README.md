# Esds Companion

An Android Studio / IntelliJ plugin for the
[Infomaniak Android design system](https://github.com/Infomaniak/android-design-system) (ESDS). It
shows the real value behind dimension and opacity tokens while you write Kotlin, which the IDE can't do by
itself.

## Features

### Values in code completion

The completion popup shows each token's actual value instead of the `Dp` or `Float` type:

```
EsdsTheme.icon.|
                sizeXs      16dp
                sizeSm      20dp
                sizeMd      24dp
                sizeLg      32dp
                sizeXl      40dp
```

### Find a token by its value

If you know the value but not the token name, type the number instead:

```
EsdsTheme.spacing.1|              EsdsTheme.spacing.16|
                  lg       12dp                     xl    16dp
                  xl       16dp
                  eightXl  100dp
```

Tokens whose value **starts with** the digits are listed, smallest first. Picking one replaces the
digits with the token name, so `EsdsTheme.spacing.16` becomes `EsdsTheme.spacing.xl`.

### Inlay hints

The value also appears inline, next to each call site:

```kotlin
Box(Modifier.padding(EsdsTheme.spacing.md 8 dp))
```

Inlay hints are **on by default**. To turn them off, go to **Settings → Editor → Inlay Hints → Values → _Infomaniak
design system token values_**.

**Ctrl-click** (**Cmd-click** on macOS) a hint to open the design system source that defines the
token, for example the `sizeSm = …` line in `DefaultIconTokens.kt`. This needs the design system's
sources in your project. If navigation reports missing sources, enable *Settings → Build Tools → Gradle → Download
sources* and re-sync.

### Supported tokens

| Accessor            | Example                   | Shown                               |
|---------------------|---------------------------|-------------------------------------|
| `EsdsTheme.icon`    | `EsdsTheme.icon.sizeSm`   | `20dp`                              |
| `EsdsTheme.spacing` | `EsdsTheme.spacing.md`    | `8dp`                               |
| `EsdsTheme.radius`  | `EsdsTheme.radius.lg`     | `8dp` (`full` for the circle shape) |
| `EsdsTheme.opacity` | `EsdsTheme.opacity.ghost` | `5%` (the `0.05f` alpha)            |

Per-app themes work too, as long as their name ends in `Theme` (`MailTheme.spacing.md`).

## Installation

Requires Android Studio Meerkat (2024.3, build 243) or any IntelliJ-based IDE of that version or
newer. There is no upper version bound.

1. Download `EsdsCompanion-<version>.zip` from the Releases page, or [build it](#building).
2. In the IDE, open **Settings → Plugins → ⚙ → Install Plugin from Disk…** and select the zip.
3. Restart the IDE.

> **Upgrading from "Infomaniak Design System Token Info":** this is the same plugin under a new
> name and plugin ID, so the IDE won't replace the old one. Uninstall the old plugin first, or both
> will decorate your code.

## Limitations

- **Values come from the default theme.** Tokens are provided through a Compose `CompositionLocal`,
  so their values don't exist until runtime. The plugin ships values generated from the design
  system's default theme. Every current app theme maps these tokens to the same values, but values
  overridden locally with `CompositionLocalProvider` aren't reflected.
- **Receivers are matched by text.** `EsdsTheme.spacing.md` is recognised, but a receiver stored in
  a variable (`val s = EsdsTheme.spacing; s.md`) is not.
- **Dimension and opacity tokens only.** Colors and typography are out of scope.

## Contributing

### Building

```bash
./gradlew buildPlugin                 # zip in build/distributions/
./gradlew test                        # unit + integration tests (Kotlin plugin in K2 mode)
./gradlew test -PkotlinK2Mode=false   # same suite against K1
./gradlew runIde                      # sandbox IDE with the plugin installed
./gradlew verifyPlugin                # compatibility check across supported IDE versions
```

Requires JDK 21.

### Updating token values

Values are **generated, not hand-written**. `scripts/update-tokens.py` reads the design system's
default theme from GitHub and follows each token down to its literal:

```
DefaultIconTokens.sizeSm  ->  IntermediateDefault.IconSizeSm  ->  Scale20  ->  20.dp
```

and regenerates `src/main/kotlin/com/infomaniak/esdscompanion/EsdsTokenValues.kt`:

```bash
python3 scripts/update-tokens.py              # design system `main` branch
python3 scripts/update-tokens.py --ref 1.2.0  # or a specific tag
```

New tokens in an existing category are picked up automatically. A new category (a new
`EsdsTheme.xxx` accessor) must be added to `CATEGORIES` and its primitives file to `PRIMITIVE_FILES`
in the script; if its literals are neither `Dp`, shapes nor `Float`s, teach `render_primitive` to
display them. Bump `pluginVersion` in `gradle.properties`
before publishing a new build; otherwise the IDE treats the new build as already installed.

### How it works

#### Inlay hints

`EsdsTokenInlayHintsProvider` is a *declarative* inlay provider, which is what gives the per-user
on/off switch in Settings for free — no custom settings UI is needed. It matches whole
`SomeTheme.category.token` expressions; a nested receiver such as `EsdsTheme.icon` is a
dot-qualified expression too, but it is not a complete token reference, so no hint is emitted twice
for the same expression.

#### Completion by value

Kotlin lexes `spacing.16` as `spacing` followed by the number literal `.16`, so the Kotlin plugin
has nothing to offer there. `EsdsValueCompletion` takes over that context entirely: it adds the
category's tokens with a prefix matcher that compares the typed digits against each token's *value*
rather than its name, then stops the remaining contributors.

The popup opened by typing `EsdsTheme.spacing.` only holds names, which digits can never match, so
it would simply close. The name branch therefore calls `restartCompletionOnPrefixChange` for
all-digit prefixes, which reruns completion and lands in the value branch instead.

#### Jumping to the definition

Clicking a hint opens the file that states what the token is worth. The design system reaches the
literal through three files, so `EsdsTokenNavigator` looks for the closest one available in the
project and falls back outwards:

| # | File                      | Line matched                              | Why                                               |
|---|---------------------------|-------------------------------------------|---------------------------------------------------|
| 1 | `DefaultIconTokens.kt`    | `sizeSm = IntermediateDefault.IconSizeSm` | where the token is assigned a value               |
| 2 | `IntermediateDefault.kt`  | `val IconSizeSm = Scale20`                | if the app only depends on the intermediate layer |
| 3 | `ScalePrimitiveTokens.kt` | `val Scale20 = 20.dp`                     | last resort: the raw literal                      |

Files are found through the core `FilenameIndex` and scanned with a regex, again to avoid depending
on the Kotlin plugin's PSI. The assignment pattern is guarded with `(?<![.\w])` so that searching for
`md` in `DefaultSpacingTokens.kt` matches the `md =` on the left-hand side and not the
`IntermediateDefault.SpacingMd` on the right.

The lookup runs on click, not while hints are being built, because the hints pass re-runs on every
keystroke, and it runs off the EDT in smart mode, since querying the index and reading a file out of
a jar are both slow operations.

The file is then opened through the `VirtualFile` that was found, never through its path: the design
system is usually a library, so the file sits inside a jar and a rebuilt `file://` URL resolves to
nothing. When sources are unavailable altogether — Gradle source downloads turned off — the click
reports it in the editor rather than silently doing nothing.

#### Code completion

`EsdsTokenCompletionContributor` is registered with `order="first"`, calls
`runRemainingContributors` to intercept the items produced by the Kotlin plugin, and re-renders the
matching ones with the token value as type text.

The receiver (`EsdsTheme.icon` vs `EsdsTheme.spacing`) is detected **textually**, from the characters
preceding the caret, rather than by resolving Kotlin types. That keeps the plugin free of any
dependency on the Kotlin plugin, which is what makes it drop-in compatible with every Android Studio
release, in both K1 and K2 mode.

The receiver must be a `*Theme` qualifier, so `EsdsTheme.spacing.md` and per-app wrappers such as
`MailTheme.spacing.md` are decorated, while an unrelated API that happens to expose a `spacing`
member is left alone. The trade-off: aliasing the receiver into a local variable (`val s = EsdsTheme.spacing`) is not
detected.

> **Note on registration:** the contributor must be registered for `language="kotlin"`, *not*
> `language="any"`. The platform resolves contributors with `allForLanguageOrAny`, which appends the
> `any` ones **after** every language-specific contributor — so an `any` contributor runs last and
> `runRemainingContributors` finds nothing left to decorate. This is covered by an integration test.

### Tests

The integration tests run the real Kotlin completion and inlay hint passes against the bundled
Kotlin plugin, so they catch contributor-ordering regressions. They check, for example, that
`EsdsTheme.icon.si<caret>` renders `sizeSm` with `20dp`, and that typing `16` into an open popup
narrows it to `xl`. `PluginRegistrationTest` validates the `plugin.xml` wiring.
`EsdsTokenNavigatorTest` runs the definition lookup against verbatim copies of the design system
files.

## License

[GPL-3.0](LICENSE), like the Infomaniak design system.

## Feedback

This is mostly vibe coded as a quick useful plugin, any feedback is appreciated. 
