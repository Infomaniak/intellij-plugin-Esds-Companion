# Infomaniak Design System Token Info

An IntelliJ / **Android Studio** plugin that shows the *actual value* of
[Infomaniak design system](https://github.com/Infomaniak/android-design-system) tokens directly in
code completion.

Instead of the useless `Dp` type on the right of the completion popup, you get the real dimension:

```
EsdsTheme.icon.|
                sizeXs      16dp
                sizeSm      20dp
                sizeMd      24dp
                sizeLg      32dp
                sizeXl      40dp
```

## Finding a token by its value

When you know the dimension but not the token name, type the number instead:

```
EsdsTheme.spacing.1|              EsdsTheme.spacing.16|
                  lg       12dp                     xl    16dp
                  xl       16dp
                  eightXl  100dp
```

Items are kept when their value **starts with** the digits, smallest first, and accepting one
replaces the digits with the token name (`EsdsTheme.spacing.xl`). This works for every category —
`icon`, `spacing`, `radius` and any added later.

## Inlay hints (optional)

The same values can be shown inline, right at each call site:

```kotlin
Box(Modifier.padding(EsdsTheme.spacing.md 8dp))
```

This is **off by default**. Each developer turns it on for themselves under
**Settings → Editor → Inlay Hints → Values → _Infomaniak design system token values_**.

Hints are clickable: **Ctrl-click** (**Cmd-click** on macOS) one to open the design system file that
defines the token — see [Jumping to the definition](#jumping-to-the-definition).

Supported token groups:

| Accessor             | Example                      | Shown |
|----------------------|------------------------------|-------|
| `EsdsTheme.icon`     | `EsdsTheme.icon.sizeSm`      | `20dp` |
| `EsdsTheme.spacing`  | `EsdsTheme.spacing.md`       | `8dp` |
| `EsdsTheme.radius`   | `EsdsTheme.radius.lg`        | `8dp` (`full` for the circle shape) |

## Installing / sharing with the team

1. Build the distributable:
   ```bash
   ./gradlew buildPlugin
   ```
   The zip lands in `build/distributions/DsTokenInfoPlugin-<version>.zip`.
2. Share that zip (Slack, an internal share, or a GitHub release).
3. Each teammate installs it in Android Studio via
   **Settings → Plugins → ⚙ → Install Plugin from Disk…** and restarts.

Compatible with Android Studio Meerkat (2024.3 / build 243) and newer — there is no upper bound, so
the plugin keeps working after IDE upgrades. Compatibility is checked at build time with
`./gradlew verifyPlugin`.

## Where the values come from

The tokens are resolved through a Compose `CompositionLocal`
(`EsdsTheme.LocalEsdsTheme`), so their value simply does not exist at edit time — the IDE cannot
evaluate it. The values are therefore baked into the plugin.

They are **not typed by hand**. `scripts/update-tokens.py` reads the design system's *default theme*
straight from GitHub and follows the token indirection down to the primitive literal:

```
DefaultIconTokens.sizeSm  ->  IntermediateDefault.IconSizeSm  ->  Scale20  ->  20.dp
```

and writes `src/main/kotlin/com/infomaniak/dstokeninfo/EsdsTokenValues.kt`.

Every app theme (Mail, kDrive, kChat, Calendar, Contacts, Euria, kNote, Security, SwissTransfer,
Infomaniak) currently maps these dimension tokens to the exact same primitives, so the default theme
is a faithful source for all of them. Locally overridden values (`CompositionLocalProvider`) are not
taken into account — by design, since in practice they never change.

Refresh the values after a design system update:

```bash
python3 scripts/update-tokens.py            # reads the `main` branch
python3 scripts/update-tokens.py --ref 1.2.0  # or a specific tag
```

If the design system ever gains a new token, the script picks it up automatically; if a theme starts
diverging from the default one, the values here would need a per-theme strategy instead.

## How it works

### Inlay hints

`EsdsTokenInlayHintsProvider` is a *declarative* inlay provider, which is what gives the per-user
on/off switch in Settings for free — no custom settings UI is needed. It matches whole
`SomeTheme.category.token` expressions; a nested receiver such as `EsdsTheme.icon` is a
dot-qualified expression too, but it is not a complete token reference, so no hint is emitted twice
for the same expression.

### Completion by value

Kotlin lexes `spacing.16` as `spacing` followed by the number literal `.16`, so the Kotlin plugin
has nothing to offer there. `EsdsValueCompletion` takes over that context entirely: it adds the
category's tokens with a prefix matcher that compares the typed digits against each token's *value*
rather than its name, then stops the remaining contributors.

The popup opened by typing `EsdsTheme.spacing.` only holds names, which digits can never match, so
it would simply close. The name branch therefore calls `restartCompletionOnPrefixChange` for
all-digit prefixes, which reruns completion and lands in the value branch instead.

### Jumping to the definition

Clicking a hint opens the file that states what the token is worth. The design system reaches the
literal through three files, so `EsdsTokenNavigator` looks for the closest one available in the
project and falls back outwards:

| # | File | Line matched | Why |
|---|------|--------------|-----|
| 1 | `DefaultIconTokens.kt` | `sizeSm = IntermediateDefault.IconSizeSm` | where the token is assigned a value |
| 2 | `IntermediateDefault.kt` | `val IconSizeSm = Scale20` | if the app only depends on the intermediate layer |
| 3 | `ScalePrimitiveTokens.kt` | `val Scale20 = 20.dp` | last resort: the raw literal |

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

### Code completion

`EsdsTokenCompletionContributor` is registered with `order="first"`, calls
`runRemainingContributors` to intercept the items produced by the Kotlin plugin, and re-renders the
matching ones with the token value as type text.

The receiver (`EsdsTheme.icon` vs `EsdsTheme.spacing`) is detected **textually**, from the characters
preceding the caret, rather than by resolving Kotlin types. That keeps the plugin free of any
dependency on the Kotlin plugin, which is what makes it drop-in compatible with every Android Studio
release, in both K1 and K2 mode.

The receiver must be a `*Theme` qualifier, so `EsdsTheme.spacing.md` and per-app wrappers such as
`MailTheme.spacing.md` are decorated, while an unrelated API that happens to expose a `spacing`
member is left alone. The trade-off: aliasing the receiver into a local variable
(`val s = EsdsTheme.spacing`) is not detected.

> **Note on registration:** the contributor must be registered for `language="kotlin"`, *not*
> `language="any"`. The platform resolves contributors with `allForLanguageOrAny`, which appends the
> `any` ones **after** every language-specific contributor — so an `any` contributor runs last and
> `runRemainingContributors` finds nothing left to decorate. This is covered by an integration test.

## Development

```bash
./gradlew test                        # unit + integration tests (Kotlin plugin in K2 mode)
./gradlew test -PkotlinK2Mode=false   # same suite against K1
./gradlew runIde                      # launch a sandbox IDE with the plugin installed
./gradlew verifyPlugin                # compatibility check against the supported IDE range
```

The integration tests drive the *real* Kotlin completion and inlay hint passes against the bundled
Kotlin plugin: they assert that `EsdsTheme.icon.si<caret>` renders `sizeSm` with `20dp`, and that
`EsdsTheme.spacing.md` gets an `8dp` inlay. That is what catches contributor-ordering regressions.
`PluginRegistrationTest` additionally checks the `plugin.xml` wiring, including that the settings
labels resolve to real resource bundle entries and that the click handler is registered under the id
the hints reference. `EsdsTokenNavigatorTest` runs the definition lookup against verbatim copies of
the real design system files.

Bump `pluginVersion` in `gradle.properties` before sharing a new build.
