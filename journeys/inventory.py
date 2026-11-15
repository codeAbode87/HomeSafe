#!/usr/bin/env python3
"""Source inventory of the app's UI surfaces, extracted from the Kotlin sources by text analysis.

A deterministic denominator for the journey model: every app, tab, page, modal, overlay, screen,
state and deep link the code declares, so `nines audit --inventory` can list what no node covers.
No Gradle, no language model; regular expressions over comment- and string-masked source.

    python3 journeys/inventory.py [--out journeys/inventory.json] [--check]

Run from the repo root. --check regenerates in memory and exits 1 if the file on disk differs.
Ids carry no line numbers (they survive unrelated edits); `source` is `path:line`.
"""
import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SOURCE_DIRS = ["shared/src/commonMain", "app/src/commonMain"] + sorted(
    os.path.relpath(p, ROOT) for p in glob.glob(os.path.join(ROOT, "features/*/src/commonMain"))
)

SHELL_ROUTE_FILES = {"App.kt", "FrigateAppShell.kt", "SettingsTabNav.kt"}
DRAWER_TAB_ENUMS = ["FinanceTab", "WeatherTab", "FitnessTab", "AtlasTab", "PackagesTab", "ChargingTab"]
PAGE_INTERFACES = ["FinanceDetail", "WeatherPage", "FitnessPage", "AtlasPage", "PackagesPage", "ChargingPage"]
PAGE_ENUMS = ["WalletTopic", "SettingsFold", "CheckKind"]
STATE_SUFFIX = re.compile(r"(UiState|Problem|Access|SensorState|SyncStatus|Signal|LoadState)$")
MODAL_KINDS = ["AlertDialog", "ModalBottomSheet", "DropdownMenu", "DatePickerDialog", "TimePickerDialog",
               "Dialog", "Popup"]


# ---------------------------------------------------------------------------------------------
# Reading and masking


def kotlin_files():
    for d in SOURCE_DIRS:
        for dirpath, _, names in os.walk(os.path.join(ROOT, d)):
            for n in names:
                if not n.endswith(".kt"):
                    continue
                if "Preview" in n or "Fixtures" in n:
                    continue
                full = os.path.join(dirpath, n)
                rel = os.path.relpath(full, ROOT)
                if re.search(r"/(\w*Test\w*)/", rel):
                    continue
                yield rel


def mask(src):
    """Blank out comments, string and char literal contents (keeping newlines and offsets)."""
    out = list(src)
    n = len(src)
    i = 0

    def blank(a, b):
        for k in range(a, b):
            if out[k] != "\n":
                out[k] = " "

    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif src.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if src.startswith("/*", j):
                    depth += 1
                    j += 2
                elif src.startswith("*/", j):
                    depth -= 1
                    j += 2
                else:
                    j += 1
            blank(i, j)
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            blank(i + 1, j - 1)
            i = j
        elif c == '"':
            j = i + 1
            while j < n and src[j] != '"' and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            blank(i + 1, j)
            i = j + 1
        elif c == "'" and re.match(r"'(\\.[^']*|[^\\'])'", src[i:i + 12]):
            m = re.match(r"'(\\.[^']*|[^\\'])'", src[i:i + 12])
            blank(i + 1, i + m.end() - 1)
            i += m.end()
        else:
            i += 1
    return "".join(out)


class File:
    def __init__(self, rel):
        self.rel = rel
        self.base = os.path.basename(rel)[:-3]
        with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
            self.raw = f.read()
        self.text = mask(self.raw)
        self.line_starts = [0]
        for m in re.finditer("\n", self.text):
            self.line_starts.append(m.end())
        self.funs = None

    def line_of(self, pos):
        lo, hi = 0, len(self.line_starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if self.line_starts[mid] <= pos:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    def src(self, pos):
        return "%s:%d" % (self.rel, self.line_of(pos))

    def indent_of(self, pos):
        ls = self.text.rfind("\n", 0, pos) + 1
        return len(self.text[ls:pos]) - len(self.text[ls:pos].lstrip())

    def match_close(self, pos, open_ch, close_ch):
        """Index just past the bracket matching the one at pos."""
        depth, j, n = 0, pos, len(self.text)
        while j < n:
            ch = self.text[j]
            if ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth == 0:
                    return j + 1
            j += 1
        return n


# ---------------------------------------------------------------------------------------------
# Functions


FUN_RE = re.compile(r"(?<![\w.])fun\s+(?:<[^>\n]*>\s*)?(?:[\w<>?,. ]+\.)?(\w+)\s*\(")


def annotations_above(f, pos):
    """The annotation text on the lines directly above (and on) the declaration at pos."""
    ls = f.text.rfind("\n", 0, pos) + 1
    parts = [f.text[ls:pos]]
    k = ls
    while k > 0:
        pls = f.text.rfind("\n", 0, k - 1) + 1
        line = f.text[pls:k - 1].strip()
        if line.startswith("@") or (line.startswith("(") or line.endswith(")") and "@" in line):
            parts.append(line)
            k = pls
        else:
            break
    return " ".join(parts)


def funs(f):
    """[(name, start, body_start, body_end, indent, annotations)] for every fun declaration."""
    if f.funs is not None:
        return f.funs
    res = []
    for m in FUN_RE.finditer(f.text):
        start = m.start()
        paren = m.end() - 1
        after = f.match_close(paren, "(", ")")
        indent = f.indent_of(start)
        # Find the body: the first '{' or '=' at top level after the signature.
        j, n = after, len(f.text)
        body_start = body_end = None
        while j < n:
            ch = f.text[j]
            if ch == "{":
                body_start, body_end = j, f.match_close(j, "{", "}")
                break
            if ch == "=" and f.text[j + 1:j + 2] != "=":
                body_start = j
                # Expression body: until the next line at indent <= the fun's that is not a continuation.
                k = f.text.find("\n", j)
                end = n
                while 0 <= k < n:
                    nxt = f.text.find("\n", k + 1)
                    nxt = n if nxt < 0 else nxt
                    line = f.text[k + 1:nxt]
                    if line.strip() and (len(line) - len(line.lstrip())) <= indent \
                            and not line.lstrip().startswith((")", "}", ".", "?", ":", "&&", "||")):
                        end = k
                        break
                    k = nxt if nxt < n else -1
                body_end = end
                break
            if ch == "\n" and re.match(r"\s*\n|\s*(@|fun |val |var |class |object |interface |private |internal |public )", f.text[j:j + 40]) \
                    and f.text[j:after + 1].strip() == "":
                break
            if ch == "\n":
                nxt = f.text[j + 1:j + 60].lstrip()
                if re.match(r"(@|fun\b|val\b|var\b|class\b|object\b|interface\b|private\b|internal\b|public\b|data\b|enum\b|sealed\b)", nxt) \
                        and len(f.text[j + 1:j + 60]) - len(nxt) <= indent:
                    break  # abstract / interface declaration without a body
            j += 1
        if body_start is None:
            continue
        res.append((m.group(1), start, body_start, body_end, indent, annotations_above(f, start)))
    f.funs = res
    return res


def enclosing_fun(f, pos):
    best = None
    for fn in funs(f):
        name, start, bs, be, indent, ann = fn
        if bs <= pos < be and (best is None or start > best[1]):
            best = fn
    return best


def is_preview(ann):
    return bool(re.search(r"@Preview\w*", ann))


# ---------------------------------------------------------------------------------------------
# Declarations


def decl_header(f, m_end):
    """After a class/object name: skip generics and constructor, return (supertypes text, body_start or None)."""
    t = f.text
    j = m_end
    n = len(t)
    while j < n and t[j] in " \t":
        j += 1
    if j < n and t[j] == "<":
        j = f.match_close(j, "<", ">")
    # optional modifiers like `private constructor(`
    mm = re.match(r"\s*(?:(?:private|internal|public|protected)\s+)?(?:constructor\s*)?(?=\()", t[j:j + 40])
    if mm and t[j + mm.end():j + mm.end() + 1] == "(":
        j = f.match_close(j + mm.end(), "(", ")")
    k = j
    supers = ""
    mm = re.match(r"\s*:", t[k:k + 20])
    if mm:
        k += mm.end()
        # supertypes run to '{' or the end of the declaration (a line that does not continue)
        depth = 0
        s = k
        while k < n:
            ch = t[k]
            if ch in "(<[":
                depth += 1
            elif ch in ")>]":
                depth -= 1
            elif ch == "{" and depth <= 0:
                break
            elif ch == "\n" and depth <= 0:
                nxt = t[k + 1:k + 80].lstrip()
                if not nxt.startswith(("," , "where")) and not t[s:k].rstrip().endswith(","):
                    break
            k += 1
        supers = t[s:k]
    body = None
    mm = re.match(r"\s*\{", t[k:k + 40])
    if mm:
        body = k + mm.end() - 1
    return supers, body


DECL_RE = re.compile(
    r"(?<![\w.])((?:(?:sealed|data|value|enum|abstract|open|private|internal|public|inner|annotation|fun)\s+)*)"
    r"(class|object|interface)\s+(\w+)")


def declarations(files):
    """Every class/object/interface: dict(name, kind, mods, file, pos, supers, body_start)."""
    decls = []
    for f in files:
        for m in DECL_RE.finditer(f.text):
            mods = m.group(1).split()
            # `companion object` and anonymous `object :` are not declarations of interest
            pre = f.text[max(0, m.start() - 12):m.start()]
            if m.group(2) == "object" and pre.rstrip().endswith("companion"):
                continue
            supers, body = decl_header(f, m.end())
            decls.append(dict(name=m.group(3), kind=m.group(2), mods=mods, file=f, pos=m.start(),
                              supers=supers, body=body))
    return decls


def super_names(supers):
    """Bare type names (last identifier of each dotted path) among the supertypes."""
    out = []
    depth = 0
    cur = ""
    for ch in supers:
        if ch in "(<[":
            depth += 1
        elif ch in ")>]":
            depth -= 1
        elif ch == "," and depth == 0:
            out.append(cur)
            cur = ""
            continue
        if depth == 0 and ch not in "()<>[]":
            cur += ch
    out.append(cur)
    names = []
    for part in out:
        part = part.strip()
        mm = re.match(r"([\w.]+)", part)
        if mm:
            names.append(mm.group(1).split(".")[-1])
    return names


def subtypes(decls, parent):
    """Direct object/class subtypes of `parent`, anywhere (nested or declared beside it)."""
    return [d for d in decls
            if d["kind"] in ("class", "object") and "enum" not in d["mods"]
            and parent in super_names(d["supers"])]


def enum_entries(f, decl):
    """[(name, pos)] of an enum class's entries."""
    t = f.text
    body = decl["body"]
    if body is None:
        return []
    end = f.match_close(body, "{", "}")
    j = body + 1
    res = []
    while j < end - 1:
        mm = re.match(r"\s*(?:@\w+(?:\([^)]*\))?\s*)*(\w+)", t[j:end])
        if not mm or not re.match(r"[A-Z_][A-Z0-9_]*$|[A-Za-z]\w*$", mm.group(1)):
            break
        name = mm.group(1)
        pos = j + mm.start(1)
        # the entry list ends at the first thing that is not `Name`, `Name(args)`, `Name { }` and a comma
        k = j + mm.end()
        if t[k:k + 1] == "(":
            k = f.match_close(k, "(", ")")
        mm2 = re.match(r"\s*\{", t[k:end])
        if mm2:
            k = f.match_close(k + mm2.end() - 1, "{", "}")
        res.append((name, pos))
        mm3 = re.match(r"\s*,", t[k:end])
        if mm3:
            j = k + mm3.end()
            continue
        break
    return res


# ---------------------------------------------------------------------------------------------
# Extraction


def run():
    files = [File(r) for r in sorted(kotlin_files())]
    decls = declarations(files)
    items = []

    def add(kind, id_, label, f, pos):
        items.append({"id": id_, "kind": kind, "label": label, "source": f.src(pos)})

    def find_decl(name, kind=None):
        c = [d for d in decls if d["name"] == name and (kind is None or d["kind"] == kind)]
        return c

    def humanize(s):
        return re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", s).replace("_", " ").lower().capitalize() \
            if not s.isupper() else s.replace("_", " ").capitalize()

    # app
    for d in find_decl("InnerApp"):
        if "enum" in d["mods"]:
            for name, pos in enum_entries(d["file"], d):
                add("app", "app:" + name, "Drawer app " + humanize(name), d["file"], pos)

    # tab: top-level routes and drawer tab enums
    for d in find_decl("TopLevelRoute"):
        for s in subtypes(decls, "TopLevelRoute"):
            add("tab", "tab:TopLevelRoute." + s["name"], "Shell tab " + s["name"], s["file"], s["pos"])
        break
    for en in DRAWER_TAB_ENUMS:
        for d in find_decl(en):
            if "enum" in d["mods"]:
                for name, pos in enum_entries(d["file"], d):
                    add("tab", "tab:%s.%s" % (en, name), "%s tab %s" % (en[:-3], humanize(name)), d["file"], pos)

    # page: sealed page interfaces, shell routes, selector enums
    for pi in PAGE_INTERFACES:
        for s in subtypes(decls, pi):
            add("page", "page:%s.%s" % (pi, s["name"]), "%s page %s" % (pi, s["name"]), s["file"], s["pos"])
    for d in decls:
        if d["kind"] in ("class", "object") and "enum" not in d["mods"] and d["name"].endswith("Route") \
                and d["file"].base + ".kt" in SHELL_ROUTE_FILES:
            add("page", "page:route." + d["name"], "Navigation route " + d["name"], d["file"], d["pos"])
    for en in PAGE_ENUMS:
        for d in find_decl(en):
            if "enum" in d["mods"]:
                for name, pos in enum_entries(d["file"], d):
                    add("page", "page:%s.%s" % (en, name), "%s %s" % (en, humanize(name)), d["file"], pos)

    # modal and overlay (counted by enclosing function)
    call_re = re.compile(r"(?<![\w.])(%s)\s*\(" % "|".join(MODAL_KINDS))
    by_fn = {}
    for f in files:
        for m in call_re.finditer(f.text):
            line_start = f.text.rfind("\n", 0, m.start()) + 1
            if re.search(r"\bfun\s+$", f.text[line_start:m.start()]):
                continue
            fn = enclosing_fun(f, m.start())
            if fn and is_preview(fn[5]):
                continue
            by_fn.setdefault((f.base, fn[0] if fn else "(file)"), []).append((f, m.start(), m.group(1)))
    # #2, #3 count every modal surface of the function, in source order
    for (base, name), sites in by_fn.items():
        for i, (f, pos, mk) in enumerate(sorted(sites, key=lambda s: s[1]), 1):
            add("modal", "modal:%s.%s" % (base, name) + ("" if i == 1 else "#%d" % i), mk, f, pos)

    pb_re = re.compile(r"(?<![\w.])rememberPredictiveBack\s*\(")
    ov = {}
    for f in files:
        for m in pb_re.finditer(f.text):
            line_start = f.text.rfind("\n", 0, m.start()) + 1
            if re.search(r"\bfun\s+$", f.text[line_start:m.start()]):
                continue
            fn = enclosing_fun(f, m.start())
            if fn and is_preview(fn[5]):
                continue
            ov.setdefault((f.base, fn[0] if fn else "(file)"), []).append((f, m.start()))
    for (base, name), sites in ov.items():
        for i, (f, pos) in enumerate(sorted(sites, key=lambda s: s[1]), 1):
            add("overlay", "overlay:%s.%s" % (base, name) + ("" if i == 1 else "#%d" % i),
                "Back-owning surface " + name, f, pos)

    # screen
    for f in files:
        for name, start, bs, be, indent, ann in funs(f):
            if indent == 0 and name.endswith("Screen") and "@Composable" in ann and not is_preview(ann):
                add("screen", "screen:" + name, name, f, start)

    # state
    for d in decls:
        n = d["name"]
        if not (STATE_SUFFIX.search(n) or n == "ConnectUiState"):
            continue
        f = d["file"]
        if "enum" in d["mods"]:
            for name, pos in enum_entries(f, d):
                add("state", "state:%s.%s" % (n, name), "%s %s" % (n, humanize(name)), f, pos)
        elif "sealed" in d["mods"]:
            for s in subtypes(decls, n):
                add("state", "state:%s.%s" % (n, s["name"]), "%s %s" % (n, s["name"]), s["file"], s["pos"])

    # deeplink
    for d in decls:
        if d["kind"] in ("class", "object") and d["name"].endswith("DeepLink") \
                and "/navigation/" in d["file"].rel and "enum" not in d["mods"]:
            add("deeplink", "deeplink:" + d["name"], d["name"], d["file"], d["pos"])
    for s in subtypes(decls, "ShellCommand"):
        add("deeplink", "deeplink:ShellCommand." + s["name"], "Shell command " + s["name"], s["file"], s["pos"])

    # uniqueness: identical ids (same name declared twice) get a numeric suffix by source order
    items.sort(key=lambda it: (it["id"], it["source"]))
    final, count = [], {}
    for it in items:
        count[it["id"]] = count.get(it["id"], 0) + 1
        if count[it["id"]] > 1:
            it["id"] += "#%d" % count[it["id"]]
        final.append(it)
    final.sort(key=lambda it: it["id"])

    # stats
    kinds = {}
    for it in final:
        kinds[it["kind"]] = kinds.get(it["kind"], 0) + 1
    return {"items": final, "stats": {"items_per_kind": dict(sorted(kinds.items())),
                                      "test_tags_per_feature": tag_counts(files)}}


def tag_counts(files):
    consts = {}
    for f in files:
        for m in re.finditer(r"const val (\w+)\s*(?::\s*String)?\s*=\s*\"", f.text):
            lit = re.match(r'"([^"\n]*)"', f.raw[m.end() - 1:])
            if lit and ("TAG" in m.group(1).upper()):
                consts[m.group(1)] = lit.group(1)
    per = {}
    for f in files:
        feat = f.rel.split("/")[1] if f.rel.startswith("features/") else f.rel.split("/")[0]
        tags = per.setdefault(feat, set())
        for m in re.finditer(r"testTag\(\s*([^,)\n]+)", f.raw):
            arg = m.group(1).strip()
            lit = re.match(r'"([^"]*)"', arg)
            if lit:
                tags.add(lit.group(1))
            elif arg in consts:
                tags.add(consts[arg])
            else:
                tags.add("<" + arg + ">")
    return {k: len(v) for k, v in sorted(per.items())}


def render(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="journeys/inventory.json")
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    os.chdir(ROOT)
    text = render(run())
    if a.check:
        try:
            with open(a.out, encoding="utf-8") as f:
                cur = f.read()
        except FileNotFoundError:
            cur = None
        if cur != text:
            print("%s is out of date: run python3 journeys/inventory.py" % a.out, file=sys.stderr)
            sys.exit(1)
        print("%s is up to date" % a.out)
        return
    with open(a.out, "w", encoding="utf-8") as f:
        f.write(text)
    stats = json.loads(text)["stats"]["items_per_kind"]
    print("wrote %s: %d items %s" % (a.out, sum(stats.values()), stats))


if __name__ == "__main__":
    main()
