#!/usr/bin/env python3
"""Static sanity checks for the Kotlin sources (no toolchain required).

1. Every `import com.livora.corbett...` resolves to a declaration in the project.
2. Project symbols used in a file from ANOTHER package are imported (catches missing imports).
3. Braces / parentheses / brackets are balanced per file (strings and comments ignored).
4. Duplicate top-level declarations inside one package.
5. Leftover junk markers (TODO / FIXME / unused suppressions).

Usage: python3 tools/check_imports.py   (exit code 1 on errors)
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src")
BASE = "com.livora.corbett"

DECL = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|protected|data|sealed|enum|abstract|open|inline|value|annotation|const|lateinit|suspend|operator|infix|tailrec|override|fun\s+interface)\s+)*"
    r"(class|interface|object|typealias|fun|val|var)\s+(?:<[^>]+>\s*)?(?:[A-Za-z0-9_.<>?, ]+\.)?([A-Za-z_][A-Za-z0-9_]*)",
    re.M,
)
PKG = re.compile(r"^package\s+([\w.]+)", re.M)
IMPORT = re.compile(r"^import\s+([\w.]+?)(?:\s+as\s+(\w+))?(\.\*)?\s*$", re.M)

KNOWN = {
    "Modifier": "androidx.compose.ui.Modifier", "Composable": "androidx.compose.runtime.Composable",
    "dp": "androidx.compose.ui.unit.dp", "sp": "androidx.compose.ui.unit.sp",
    "Column": "androidx.compose.foundation.layout.Column", "Row": "androidx.compose.foundation.layout.Row",
    "Box": "androidx.compose.foundation.layout.Box", "Spacer": "androidx.compose.foundation.layout.Spacer",
    "Arrangement": "androidx.compose.foundation.layout.Arrangement", "Alignment": "androidx.compose.ui.Alignment",
    "Text": "androidx.compose.material3.Text", "Icon": "androidx.compose.material3.Icon",
    "IconButton": "androidx.compose.material3.IconButton", "MaterialTheme": "androidx.compose.material3.MaterialTheme",
    "TextButton": "androidx.compose.material3.TextButton", "AlertDialog": "androidx.compose.material3.AlertDialog",
    "Icons": "androidx.compose.material.icons.Icons", "Color": "androidx.compose.ui.graphics.Color",
    "remember": "androidx.compose.runtime.remember", "mutableStateOf": "androidx.compose.runtime.mutableStateOf",
    "LaunchedEffect": "androidx.compose.runtime.LaunchedEffect", "DisposableEffect": "androidx.compose.runtime.DisposableEffect",
    "rememberCoroutineScope": "androidx.compose.runtime.rememberCoroutineScope",
    "LazyColumn": "androidx.compose.foundation.lazy.LazyColumn", "LazyRow": "androidx.compose.foundation.lazy.LazyRow",
    "clickable": "androidx.compose.foundation.clickable", "background": "androidx.compose.foundation.background",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize", "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "padding": "androidx.compose.foundation.layout.padding", "size": "androidx.compose.foundation.layout.size",
    "height": "androidx.compose.foundation.layout.height", "width": "androidx.compose.foundation.layout.width",
    "clip": "androidx.compose.ui.draw.clip", "RoundedCornerShape": "androidx.compose.foundation.shape.RoundedCornerShape",
    "CircleShape": "androidx.compose.foundation.shape.CircleShape", "hiltViewModel": "androidx.hilt.navigation.compose.hiltViewModel",
    "collectAsStateWithLifecycle": "androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "viewModelScope": "androidx.lifecycle.viewModelScope", "HiltViewModel": "dagger.hilt.android.lifecycle.HiltViewModel",
    "Inject": "javax.inject.Inject", "MutableStateFlow": "kotlinx.coroutines.flow.MutableStateFlow",
    "StateFlow": "kotlinx.coroutines.flow.StateFlow", "asStateFlow": "kotlinx.coroutines.flow.asStateFlow",
    
    "Serializable": "kotlinx.serialization.Serializable", "SerialName": "kotlinx.serialization.SerialName",
    "LocalContext": "androidx.compose.ui.platform.LocalContext", "FontWeight": "androidx.compose.ui.text.font.FontWeight",
    "TextOverflow": "androidx.compose.ui.text.style.TextOverflow", "TextAlign": "androidx.compose.ui.text.style.TextAlign",
    "ImeAction": "androidx.compose.ui.text.input.ImeAction", "KeyboardType": "androidx.compose.ui.text.input.KeyboardType",
    "ImageVector": "androidx.compose.ui.graphics.vector.ImageVector", "graphicsLayer": "androidx.compose.ui.graphics.graphicsLayer",
    "PaddingValues": "androidx.compose.foundation.layout.PaddingValues", "Brush": "androidx.compose.ui.graphics.Brush",
    "ViewModel": "androidx.lifecycle.ViewModel", "SavedStateHandle": "androidx.lifecycle.SavedStateHandle",
    "Context": "android.content.Context", "Scaffold": "androidx.compose.material3.Scaffold",
    "Surface": "androidx.compose.material3.Surface", "CircularProgressIndicator": "androidx.compose.material3.CircularProgressIndicator",
    "HorizontalDivider": "androidx.compose.material3.HorizontalDivider", "ModalBottomSheet": "androidx.compose.material3.ModalBottomSheet",
    "AnimatedVisibility": "androidx.compose.animation.AnimatedVisibility", "animateFloatAsState": "androidx.compose.animation.core.animateFloatAsState",
    "tween": "androidx.compose.animation.core.tween", "Animatable": "androidx.compose.animation.core.Animatable",
    "rememberScrollState": "androidx.compose.foundation.rememberScrollState", "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll", "border": "androidx.compose.foundation.border",
    "offset": "androidx.compose.foundation.layout.offset", "weight": None, "Canvas": "androidx.compose.foundation.Canvas",
    "semantics": "androidx.compose.ui.semantics.semantics", "contentDescription": "androidx.compose.ui.semantics.contentDescription",
    "Offset": "androidx.compose.ui.geometry.Offset", "Size": "androidx.compose.ui.geometry.Size",
    "SideEffect": "androidx.compose.runtime.SideEffect", "derivedStateOf": "androidx.compose.runtime.derivedStateOf",
    "rememberSaveable": "androidx.compose.runtime.saveable.rememberSaveable",
    "ExperimentalMaterial3Api": "androidx.compose.material3.ExperimentalMaterial3Api",
    "Dp": "androidx.compose.ui.unit.Dp", "TextStyle": "androidx.compose.ui.text.TextStyle",
    "delay": "kotlinx.coroutines.delay", "Flow": "kotlinx.coroutines.flow.Flow", "map": None,
    "LocalDensity": "androidx.compose.ui.platform.LocalDensity", "ContentScale": "androidx.compose.ui.layout.ContentScale",
}


def strip(src: str) -> str:
    """Remove comments and string/char literals (keeps newlines) so brace counting is reliable."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            while i < n and src[i] != "\n":
                i += 1
        elif src.startswith("/*", i):
            depth = 1
            i += 2
            while i < n and depth:
                if src.startswith("/*", i):
                    depth += 1; i += 2
                elif src.startswith("*/", i):
                    depth -= 1; i += 2
                else:
                    if src[i] == "\n": out.append("\n")
                    i += 1
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append("\n" * src.count("\n", i, j))
            out.append('""')
            i = j
        elif c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\": i += 1
                elif src.startswith("${", i):
                    # skip a template expression
                    d = 1; i += 2
                    while i < n and d:
                        if src[i] == "{": d += 1
                        elif src[i] == "}": d -= 1
                        i += 1
                    continue
                i += 1
            i += 1
            out.append('""')
        elif c == "'" and i + 2 < n and (src[i + 2] == "'" or src[i + 1] == "\\"):
            j = src.find("'", i + 2)
            j = i + 3 if j < 0 else j + 1
            out.append("' '")
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def main() -> int:
    files = []
    for d, _, fs in os.walk(ROOT):
        for f in fs:
            if f.endswith(".kt"):
                files.append(os.path.join(d, f))
    files.sort()

    info = {}
    top_by_pkg = defaultdict(lambda: defaultdict(set))   # pkg -> name -> files
    all_names_by_file = {}
    for p in files:
        raw = open(p, encoding="utf-8").read()
        code = strip(raw)
        m = PKG.search(code)
        pkg = m.group(1) if m else ""
        tops, alls = set(), set()
        for mm in DECL.finditer(code):
            name = mm.group(2)
            alls.add(name)
            ls = code.rfind("\n", 0, mm.start(1)) + 1
            if mm.start(1) - ls < 40 and not code[ls:mm.start(1)].startswith((" ", "\t")):
                tops.add(name)
        # enum entries and constructor properties count as members for nested imports
        for mm in re.finditer(r"\b([A-Z][A-Z0-9_]{1,})\b\s*[,;(]", code):
            alls.add(mm.group(1))
        for mm in re.finditer(r"(?:val|var)\s+(\w+)", code):
            alls.add(mm.group(1))
        info[p] = (pkg, code, raw, tops, alls)
        all_names_by_file[p] = alls
        for t in tops:
            top_by_pkg[pkg][t].add(p)

    errors, warns = [], []
    pkgs = set(top_by_pkg) | {v[0] for v in info.values()}

    # 1. import resolution
    for p, (pkg, code, raw, tops, alls) in info.items():
        rel = os.path.relpath(p, ROOT)
        for mm in IMPORT.finditer(code):
            path, alias, star = mm.group(1), mm.group(2), mm.group(3)
            if not path.startswith(BASE) or path in (BASE + ".BuildConfig", BASE + ".R"):
                continue
            if star:
                if path not in pkgs and not any(path.endswith("." + t) for t in top_by_pkg.get(path.rsplit(".", 1)[0], {})):
                    errors.append(f"{rel}: wildcard import of unknown package {path}.*")
                continue
            parts = path.split(".")
            ok = False
            for cut in range(len(parts) - 1, 0, -1):
                pk = ".".join(parts[:cut])
                if pk in top_by_pkg and parts[cut] in top_by_pkg[pk]:
                    rest = parts[cut + 1:]
                    ok = all(any(r in all_names_by_file[f] for f in top_by_pkg[pk][parts[cut]]) for r in rest)
                    break
            if not ok:
                errors.append(f"{rel}: unresolved import {path}")

    # 2. missing imports of project symbols
    owner = defaultdict(set)    # symbol -> packages declaring it at top level
    for pkg, names in top_by_pkg.items():
        for n in names:
            owner[n].add(pkg)
    skip_common = {"map", "Result", "Session", "Load", "Ref", "Msg", "Fmt", "Room", "User", "Booking"}
    for p, (pkg, code, raw, tops, alls) in info.items():
        rel = os.path.relpath(p, ROOT)
        imported = set()
        wild = set()
        for mm in IMPORT.finditer(code):
            if mm.group(3):
                wild.add(mm.group(1))
            else:
                imported.add(mm.group(2) or mm.group(1).rsplit(".", 1)[-1])
        body = re.sub(r"\bcom\.livora\.corbett(?:\.\w+)+", "", IMPORT.sub("", PKG.sub("", code)))
        words = set(re.findall(r"\b[A-Za-z_][A-Za-z0-9_]*\b", body))
        for w in words:
            pk_set = owner.get(w)
            if w in ("map",) or not pk_set or pkg in pk_set or w in imported or w in tops:
                continue
            if any(x in wild for x in pk_set):
                continue
            if not (w[0].isupper() or re.search(r"\b" + w + r"\s*[({]|\.\s*" + w + r"\b", body)):
                continue
            if w in alls and not w[0].isupper():
                continue     # locally declared name shadows the project one
            if len(pk_set) > 1 or w in skip_common and w not in {"Load", "Fmt", "Ref", "Msg"}:
                continue
            (only,) = pk_set
            (declaring,) = list(top_by_pkg[only][w])[:1] or [None]
            if w[0].isupper():
                errors.append(f"{rel}: uses {w} from {only} without importing it")
            else:
                warns.append(f"{rel}: maybe uses {only}.{w} without import (verify)")


    # 2b. well-known external symbols used without import
    for p, (pkg, code, raw, tops, alls) in info.items():
        rel = os.path.relpath(p, ROOT)
        imported, wild = set(), set()
        for mm in IMPORT.finditer(code):
            if mm.group(3): wild.add(mm.group(1))
            else: imported.add(mm.group(2) or mm.group(1).rsplit(".", 1)[-1])
        body = re.sub(r"\b(?:androidx|kotlinx|android|dagger|javax|java|com)(?:\.\w+)+", "", IMPORT.sub("", PKG.sub("", code)))
        for name, full in KNOWN.items():
            if not full or name in imported or name in alls or name in tops:
                continue
            if full.rsplit(".", 1)[0] in wild:
                continue
            if name[0].isupper():
                pat = r"(?<![\w.])" + name + r"\b"
            else:
                pat = r"(?<![\w])" + name + r"\b(?=\s*[({]|\s*$)" if name not in ("dp", "sp") else r"(?<=\d)\." + name + r"\b|\b\d+" + name + r"\b"
            if name in ("dp", "sp"):
                pat = r"[\d)]\." + name + r"\b"
            if name in ("padding", "size", "height", "width", "offset", "background", "clickable", "border", "clip", "graphicsLayer", "verticalScroll", "horizontalScroll", "fillMaxSize", "fillMaxWidth", "semantics"):
                pat = r"\.\s*" + name + r"\("
            if re.search(pat, body, re.M):
                errors.append(f"{rel}: uses {name} but does not import {full}")

    # 3. balance
    pairs = {")": "(", "]": "[", "}": "{"}
    for p, (pkg, code, raw, tops, alls) in info.items():
        rel = os.path.relpath(p, ROOT)
        stack = []
        line = 1
        bad = None
        for ch in code:
            if ch == "\n":
                line += 1
            elif ch in "([{":
                stack.append((ch, line))
            elif ch in pairs:
                if not stack or stack[-1][0] != pairs[ch]:
                    bad = f"unmatched '{ch}' at line {line}"
                    break
                stack.pop()
        if bad:
            errors.append(f"{rel}: {bad}")
        elif stack:
            errors.append(f"{rel}: unclosed '{stack[-1][0]}' opened at line {stack[-1][1]}")

    # 4. duplicate top-level names in a package (overload-able funs are ignored)
    for pkg, names in top_by_pkg.items():
        for n, fs in names.items():
            if len(fs) > 1 and n[0].isupper():
                kinds = [re.search(r"\b(class|object|interface|typealias)\s+" + n + r"\b", info[f][1]) for f in fs]
                if sum(1 for k in kinds if k) > 1:
                    errors.append(f"duplicate top-level type {pkg}.{n} in {[os.path.basename(f) for f in fs]}")

    # 5. junk markers
    for p, (pkg, code, raw, tops, alls) in info.items():
        rel = os.path.relpath(p, ROOT)
        for i, l in enumerate(raw.splitlines(), 1):
            if re.search(r"\b(TODO|FIXME|XXX)\b|Suppress\(\"unused\"\)|UNUSED", l):
                warns.append(f"{rel}:{i}: marker: {l.strip()[:80]}")

    print(f"Checked {len(files)} Kotlin files, {sum(len(v) for v in top_by_pkg.values())} top-level declarations.")
    for w in sorted(set(warns)):
        print("WARN ", w)
    for e in errors:
        print("ERROR", e)
    print(f"{len(errors)} error(s), {len(set(warns))} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
