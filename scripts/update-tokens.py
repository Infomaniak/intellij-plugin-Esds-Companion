#!/usr/bin/env python3
"""Regenerate EsdsTokenValues.kt from the Infomaniak Android design system.

The plugin cannot resolve `EsdsTheme.icon.sizeSm` at edit time (the values live behind
a Compose CompositionLocal), so the values are baked into the plugin instead. They are
not typed by hand: this script reads the *default theme* of the design system and
follows the token indirection all the way down to the primitive literals.

    DefaultIconTokens.sizeSm -> IntermediateDefault.IconSizeSm -> Scale20 -> 20.dp

Every app theme (Mail, kDrive, kChat, ...) currently maps these dimension and opacity tokens to the
exact same primitives, so the default theme is a faithful source for all of them.

Usage:
    python3 scripts/update-tokens.py [--ref main]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys

REPO = "Infomaniak/android-design-system"
RAW = "https://raw.githubusercontent.com/{repo}/{ref}/{path}"

FOUNDATION = "Foundation/src/main/kotlin/com/infomaniak/designsystem/core"
PRIMITIVES = "PrimitiveTokens/src/main/kotlin/com/infomaniak/designsystem/primitivetokens"

OUTPUT = "src/main/kotlin/com/infomaniak/esdscompanion/EsdsTokenValues.kt"

# EsdsTheme accessor -> file declaring the default mapping for that accessor.
CATEGORIES = {
    "icon": f"{FOUNDATION}/defaultvalues/DefaultIconTokens.kt",
    "spacing": f"{FOUNDATION}/defaultvalues/DefaultSpacingTokens.kt",
    "radius": f"{FOUNDATION}/defaultvalues/DefaultRadiusTokens.kt",
    "opacity": f"{FOUNDATION}/defaultvalues/DefaultOpacityTokens.kt",
}

PRIMITIVE_FILES = [
    f"{PRIMITIVES}/ScalePrimitiveTokens.kt",
    f"{PRIMITIVES}/SpacingPrimitiveTokens.kt",
    f"{PRIMITIVES}/RadiusPrimitiveTokens.kt",
    f"{PRIMITIVES}/OpacityPrimitiveTokens.kt",
]

INTERMEDIATE = f"{FOUNDATION}/defaultvalues/internal/IntermediateDefault.kt"

# File and object declaring every default token value, used as the navigation target when a
# user clicks an inlay hint.
DECLARATION_FILE_NAME = INTERMEDIATE.rsplit("/", 1)[-1]
DECLARATION_OBJECT = DECLARATION_FILE_NAME.removesuffix(".kt")

VAL_RE = re.compile(r"^\s*val\s+(\w+)\s*:\s*\w+\s*=\s*(.+?)\s*$", re.MULTILINE)
ASSIGN_RE = re.compile(r"^\s*(\w+)\s*=\s*IntermediateDefault\.(\w+)\s*,\s*$", re.MULTILINE)


def fetch(path: str, ref: str) -> str:
    # `curl` is used instead of urllib so the script keeps working behind the corporate
    # TLS-inspecting proxy, whose root CA lives in the system trust store only.
    url = RAW.format(repo=REPO, ref=ref, path=path)
    result = subprocess.run(
        ["curl", "--silent", "--show-error", "--fail", "--location", url],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"Failed to fetch {url}: {result.stderr.strip()}")
    return result.stdout


def format_number(raw: str) -> str:
    """`20` -> `20`, `1.5` -> `1.5`, `4.0` -> `4`."""
    value = float(raw)
    return str(int(value)) if value.is_integer() else str(value)


def render_primitive(expression: str) -> str:
    """Turn a primitive declaration's right-hand side into something human readable."""
    expression = expression.strip()

    dp = re.fullmatch(r"([\d.]+)\.dp", expression)
    if dp:
        return f"{format_number(dp.group(1))}dp"

    rounded = re.fullmatch(r"RoundedCornerShape\(([\d.]+)\.dp\)", expression)
    if rounded:
        return f"{format_number(rounded.group(1))}dp"

    # Opacities are Float fractions (`0.05f`); a percentage reads better next to a token name.
    fraction = re.fullmatch(r"([\d.]+)f", expression)
    if fraction:
        return f"{format_number(str(round(float(fraction.group(1)) * 100, 4)))}%"

    if expression == "RectangleShape":
        return "0dp"
    if expression == "CircleShape":
        return "full"

    return expression


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ref", default="main", help="Git ref of the design system to read")
    args = parser.parse_args()

    primitives: dict[str, str] = {}
    primitive_files: dict[str, str] = {}
    for path in PRIMITIVE_FILES:
        for name, expression in VAL_RE.findall(fetch(path, args.ref)):
            primitives[name] = render_primitive(expression)
            primitive_files[name] = path.rsplit("/", 1)[-1]

    intermediate: dict[str, str] = {}
    for name, expression in VAL_RE.findall(fetch(INTERMEDIATE, args.ref)):
        intermediate[name] = expression.strip()

    categories: dict[str, dict[str, tuple[str, str, str, str]]] = {}
    category_files: dict[str, str] = {}
    for category, path in CATEGORIES.items():
        category_files[category] = path.rsplit("/", 1)[-1]
        tokens: dict[str, tuple[str, str, str, str]] = {}
        for prop, intermediate_name in ASSIGN_RE.findall(fetch(path, args.ref)):
            primitive = intermediate.get(intermediate_name)
            if primitive is None:
                raise SystemExit(f"Unknown IntermediateDefault.{intermediate_name}")
            value = primitives.get(primitive)
            if value is None:
                raise SystemExit(f"Unknown primitive {primitive} for {category}.{prop}")
            # Both declaration sites are recorded so clicking an inlay hint can navigate to
            # the design system sources: the default theme mapping first, and the primitive
            # holding the literal (`Scale20 = 20.dp`) as a fallback.
            tokens[prop] = (value, intermediate_name, primitive, primitive_files[primitive])
        if not tokens:
            raise SystemExit(f"No tokens parsed for category '{category}' from {path}")
        categories[category] = tokens

    lines = [
        "// Generated by scripts/update-tokens.py from",
        f"// https://github.com/{REPO} ({args.ref}). Do not edit by hand.",
        "",
        "package com.infomaniak.esdscompanion",
        "",
        "/**",
        " * Resolved values of the Infomaniak design system tokens, keyed by the `EsdsTheme`",
        " * accessor they are reached through (`icon`, `spacing`, `radius`, `opacity`).",
        " */",
        "internal object EsdsTokenValues {",
        "",
        "    /**",
        "     * @param value the resolved value, e.g. `20dp` or `5%`.",
        f"     * @param declarationName the property declaring this token in `{DECLARATION_OBJECT}`,",
        "     *   e.g. `IconSizeSm`.",
        "     * @param primitiveName the primitive holding the literal, e.g. `Scale20`.",
        "     * @param primitiveFileName the file declaring [primitiveName].",
        "     */",
        "    data class Token(",
        "        val value: String,",
        "        val declarationName: String,",
        "        val primitiveName: String,",
        "        val primitiveFileName: String,",
        "    )",
        "",
        f'    const val DECLARATION_FILE_NAME: String = "{DECLARATION_FILE_NAME}"',
        "",
        "    /** File assigning the tokens of a category, e.g. `sizeSm = ...` in the icon one. */",
        "    val categoryFileNames: Map<String, String> = mapOf(",
    ]
    for category, file_name in category_files.items():
        lines.append(f'        "{category}" to "{file_name}",')
    lines += [
        "    )",
        "",
        "    val categories: Map<String, Map<String, Token>> = mapOf(",
    ]
    for category, tokens in categories.items():
        lines.append(f'        "{category}" to mapOf(')
        for prop, (value, declaration, primitive, primitive_file) in tokens.items():
            lines.append(
                f'            "{prop}" to Token("{value}", "{declaration}", '
                f'"{primitive}", "{primitive_file}"),'
            )
        lines.append("        ),")
    lines += [
        "    )",
        "",
        "    val knownCategories: Set<String> = categories.keys",
        "",
        "    fun tokenOf(category: String, token: String): Token? = categories[category]?.get(token)",
        "",
        "    fun valueOf(category: String, token: String): String? = tokenOf(category, token)?.value",
        "}",
        "",
    ]

    with open(OUTPUT, "w", encoding="utf-8") as output:
        output.write("\n".join(lines))

    total = sum(len(tokens) for tokens in categories.values())
    print(f"Wrote {OUTPUT} ({total} tokens across {len(categories)} categories)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
