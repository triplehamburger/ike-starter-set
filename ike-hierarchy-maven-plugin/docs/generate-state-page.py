#!/usr/bin/env python3
"""Generates a directed-tree view of ike-starter-set's current state as one self-contained page."""
import html, json

NODE_W, NODE_H, RX = 196, 48, 8

STATUS = {
    "pass":    ("good",     "#0ca30c", "✓", "verified"),
    "stale":   ("warning",  "#fab219", "▲", "stale build"),
    "blocked": ("critical", "#d03b3b", "✕", "blocking"),
    "pending": ("neutral",  "#898781", "○", "not built"),
}

def esc(t): return html.escape(str(t), quote=True)

def node(nid, x, y, title, sub, status, tip):
    """One box, anchored by its centre."""
    return dict(id=nid, x=x, y=y, title=title, sub=sub, status=status, tip=tip)

def render_nodes(nodes):
    out = []
    for n in nodes:
        x, y = n["x"] - NODE_W / 2, n["y"] - NODE_H / 2
        _, colour, glyph, _ = STATUS[n["status"]]
        out.append(f'''
  <g class="node" tabindex="0" data-tip="{esc(n['tip'])}">
    <rect x="{x}" y="{y}" width="{NODE_W}" height="{NODE_H}" rx="{RX}" class="box st-{n['status']}"/>
    <rect x="{x}" y="{y}" width="4" height="{NODE_H}" rx="2" fill="{colour}"/>
    <text x="{x+16}" y="{y+20}" class="n-title">{esc(n['title'])}</text>
    <text x="{x+16}" y="{y+36}" class="n-sub">{esc(n['sub'])}</text>
    <text x="{x+NODE_W-13}" y="{y+29}" class="n-glyph" fill="{colour}" text-anchor="end">{glyph}</text>
  </g>''')
    return "".join(out)

def edge(a, b, nodes, kind="solid", label=None, bow=0.0, label_at=None):
    """Right edge of a to left edge of b, as a cubic with a controllable bow."""
    A = next(n for n in nodes if n["id"] == a)
    B = next(n for n in nodes if n["id"] == b)
    x1, y1 = A["x"] + NODE_W / 2, A["y"]
    x2, y2 = B["x"] - NODE_W / 2 - 7, B["y"]
    dx = max(40, (x2 - x1) * 0.5)
    c1x, c1y = x1 + dx, y1 + bow
    c2x, c2y = x2 - dx, y2 + bow
    cls = "edge" + (" dashed" if kind == "dashed" else "")
    path = f'  <path class="{cls}" d="M{x1},{y1} C{c1x},{c1y} {c2x},{c2y} {x2},{y2}" marker-end="url(#arrow{"-d" if kind=="dashed" else ""})"/>'
    if label:
        if label_at:
            mx, my = label_at
        else:
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2 + bow * 0.75 - 6
        # A label centred on a short span lands on top of the node it came from; when the gap
        # between the two boxes is narrower than the text, the caller places it by hand instead.
        path += f'\n  <text x="{mx}" y="{my}" class="e-label" text-anchor="middle">{esc(label)}</text>'
    return path

# ----------------------------------------------------------------- reactor DAG
L = {0: 118, 1: 358, 2: 606, 3: 856}
reactor = [
    node("root", L[0], 172, "ike-starter-set", "pom · aggregator", "pending",
         "The reactor root. Its <modules> list now names 8 modules; all 9 POMs parse."),
    node("terms", L[1], 76, "ike-terms", "jar · pre-existing", "pending",
         "Untouched by this work. Not built in this session."),
    node("model", L[1], 268, "ike-hierarchy-model", "jar · new · 33 sources", "pass",
         "Built 02:54 today. 95 of 95 tests pass, 0 failures, 0 errors. Zero external dependencies — this is the module that holds every rule."),
    node("bindings", L[2], 40, "ike-bindings", "jar · pre-existing", "pending", "Depends on ike-terms. Not built in this session."),
    node("changeset", L[2], 112, "ike-changeset", "jar · pre-existing", "pending", "Depends on ike-terms. Produces ike-koncepts.yml for the guide."),
    node("plugin", L[2], 232, "ike-hierarchy-maven-plugin", "maven-plugin · new · 10 sources", "pending",
         "Never built — no target/ directory and no generated plugin.xml. This is the next step, and where maven.api.version would bite if your Maven is not on rc-5."),
    node("ext", L[2], 304, "ike-doc-extension", "jar · rewritten · 3 sources", "stale",
         "target/classes still holds ChapterIncludeProcessor from 19 Aug 20:51 — the retired code. The new sources have never been compiled."),
    node("kb", L[3], 112, "ike-kb", "pom · pre-existing", "pending", "Depends on ike-changeset."),
    node("doc", L[3], 268, "ike-doc", "pom · renders the guide", "pending",
         "Runs ike-hierarchy:index at generate-resources, then asciidoctor. Not built."),
]
reactor_edges = "\n".join([
    edge("root", "terms", reactor), edge("root", "model", reactor),
    edge("terms", "bindings", reactor), edge("terms", "changeset", reactor),
    edge("model", "plugin", reactor), edge("model", "ext", reactor),
    edge("changeset", "kb", reactor),
    edge("ext", "doc", reactor),
    edge("changeset", "doc", reactor, "dashed", bow=-26),
    edge("plugin", "doc", reactor, "dashed", "runs as plugin", bow=-34, label_at=(856, 224)),
])

# ----------------------------------------------------- document hierarchy tree
doc_nodes = [
    node("guide", 210, 74, "ike-guide", "root NOT DECLARED", "blocked",
         "ike-guide.adoc was reverted to the committed version at 15:55 today. Its ':chapter-id:' and ':chapter-root:' lines are gone, so no file declares this root."),
    node("cql", 610, 40, "cql", "cql/cql.adoc · order 1050", "blocked",
         "Declares ':chapter-parent: ike-guide', but nothing declares that root — a DanglingParent violation. ike-hierarchy:index will fail the build here."),
    node("inline", 610, 118, "21 chapters", "still written inline", "pending",
         "Language Concepts, STAMP Concepts, EL++, Field Constraints, Default Values, Tinkar Base Model and 15 more. A decompose dry run placed all 21 cleanly."),
]
doc_edges = "\n".join([
    edge("guide", "cql", doc_nodes, label="declares parent"),
    edge("guide", "inline", doc_nodes, "dashed", "awaiting decompose"),
])

# ------------------------------------------------------------------- pipeline
pipe = [
    node("src", 118, 46, "chapter .adoc files", "1 declared · 21 pending", "stale", "Only cql/cql.adoc carries a hierarchy header today."),
    node("goal", 366, 46, "ike-hierarchy:index", "scan · validate · write", "blocked", "Would fail right now on the dangling parent — by design. A chapter that cannot be placed must not vanish silently."),
    node("idx", 614, 46, "ike-hierarchy-index.json", "not generated", "pending", "The single seam. Written once per build; rendering reads only this."),
    node("proc", 862, 46, "Include processor", "resolves hierarchy: · stale", "stale", "Resolves include::hierarchy:… from the index. No directory walking, no path it was not handed."),
]
pipe_edges = "\n".join([
    edge("src", "goal", pipe), edge("goal", "idx", pipe), edge("idx", "proc", pipe),
])

STATS = [("95 / 95", "tests passing", "pass"), ("46", "Java sources written", "pass"),
         ("3", "modules added or rewritten", "pass"), ("1", "blocker", "blocked")]

rows = [
    ("ike-hierarchy-model", "jar", "verified", "Built; 95/95 tests green"),
    ("ike-hierarchy-maven-plugin", "maven-plugin", "not built", "No target/; next build step"),
    ("ike-doc-extension", "jar", "stale build", "target/ holds the retired ChapterIncludeProcessor"),
    ("ike-doc", "pom", "not built", "Wired to run ike-hierarchy:index at generate-resources"),
    ("ike-terms / bindings / changeset / kb", "jar, pom", "not built", "Pre-existing; untouched"),
    ("ike-guide root declaration", "adoc", "blocking", "Reverted at 15:55 — :chapter-root: is gone"),
    ("cql chapter", "adoc", "blocking", "Dangling parent: ike-guide is not declared"),
    ("21 inline chapters", "adoc", "pending", "Decompose dry run placed all 21 cleanly"),
]

def stat_html():
    out = []
    for v, lab, st in STATS:
        _, colour, glyph, _ = STATUS[st]
        out.append(f'<div class="stat"><div class="stat-v">{esc(v)}</div>'
                   f'<div class="stat-l"><span class="dot" style="color:{colour}">{glyph}</span>{esc(lab)}</div></div>')
    return "".join(out)

def legend_html():
    out = []
    for key in ("pass", "stale", "blocked", "pending"):
        _, colour, glyph, label = STATUS[key]
        out.append(f'<span class="lg"><span class="dot" style="color:{colour}">{glyph}</span>{esc(label)}</span>')
    return "".join(out)

def table_html():
    body = "".join(
        f"<tr><td>{esc(a)}</td><td>{esc(b)}</td><td>{esc(c)}</td><td>{esc(d)}</td></tr>"
        for a, b, c, d in rows)
    return f"<table><thead><tr><th>Component</th><th>Packaging</th><th>State</th><th>Detail</th></tr></thead><tbody>{body}</tbody></table>"

svg_defs = '''<defs>
    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M0,1 L9,5 L0,9 z" class="arrowhead"/>
    </marker>
    <marker id="arrow-d" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M0,1 L9,5 L0,9 z" class="arrowhead dim"/>
    </marker>
  </defs>'''

PAGE = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ike-starter-set — current state</title>
<style>
  .viz-root {{
    color-scheme: light;
    --surface-1:#fcfcfb; --page:#f9f9f7;
    --text-primary:#0b0b0b; --text-secondary:#52514e; --muted:#898781;
    --rule:#e1e0d9; --axis:#c3c2b7; --ring:rgba(11,11,11,0.10);
    --box:#ffffff;
  }}
  @media (prefers-color-scheme: dark) {{
    :root:where(:not([data-theme="light"])) .viz-root {{
      color-scheme: dark;
      --surface-1:#1a1a19; --page:#0d0d0d;
      --text-primary:#ffffff; --text-secondary:#c3c2b7; --muted:#898781;
      --rule:#2c2c2a; --axis:#383835; --ring:rgba(255,255,255,0.10);
      --box:#232322;
    }}
  }}
  :root[data-theme="dark"] .viz-root {{
    color-scheme: dark;
    --surface-1:#1a1a19; --page:#0d0d0d;
    --text-primary:#ffffff; --text-secondary:#c3c2b7; --muted:#898781;
    --rule:#2c2c2a; --axis:#383835; --ring:rgba(255,255,255,0.10);
    --box:#232322;
  }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:var(--page); font-family:system-ui,-apple-system,"Segoe UI",sans-serif; }}
  .viz-root {{ background:var(--page); color:var(--text-primary); padding:28px 22px 44px; }}
  .wrap {{ max-width:1010px; margin:0 auto; }}
  h1 {{ font-size:19px; margin:0 0 4px; letter-spacing:-0.01em; }}
  .sub {{ font-size:13px; color:var(--text-secondary); margin:0 0 20px; }}
  .stats {{ display:flex; gap:10px; flex-wrap:wrap; margin-bottom:22px; }}
  .stat {{ flex:1 1 150px; background:var(--surface-1); border:1px solid var(--ring); border-radius:10px; padding:12px 14px; }}
  .stat-v {{ font-size:23px; font-weight:600; letter-spacing:-0.02em; }}
  .stat-l {{ font-size:12px; color:var(--text-secondary); margin-top:2px; display:flex; align-items:center; gap:5px; }}
  .dot {{ font-size:11px; line-height:1; }}
  section {{ background:var(--surface-1); border:1px solid var(--ring); border-radius:12px; padding:16px 18px 8px; margin-bottom:16px; }}
  h2 {{ font-size:14px; margin:0 0 2px; }}
  .cap {{ font-size:12px; color:var(--text-secondary); margin:0 0 10px; }}
  .legend {{ display:flex; gap:14px; flex-wrap:wrap; font-size:12px; color:var(--text-secondary); margin:2px 0 12px; align-items:center; }}
  .lg {{ display:inline-flex; align-items:center; gap:5px; }}
  .lg-line {{ display:inline-flex; align-items:center; gap:6px; }}
  .lg-line svg {{ overflow:visible; }}
  svg.chart {{ display:block; width:100%; height:auto; overflow:visible; }}
  .box {{ fill:var(--box); stroke:var(--axis); stroke-width:1; }}
  .n-title {{ font-size:12.5px; font-weight:600; fill:var(--text-primary); }}
  .n-sub {{ font-size:10.5px; fill:var(--text-secondary); }}
  .n-glyph {{ font-size:12px; font-weight:700; }}
  .e-label {{ font-size:10px; fill:var(--muted); }}
  .edge {{ fill:none; stroke:var(--axis); stroke-width:2; }}
  .edge.dashed {{ stroke-dasharray:5 4; stroke-width:1.5; opacity:.75; }}
  .arrowhead {{ fill:var(--axis); }}
  .arrowhead.dim {{ opacity:.75; }}
  .node {{ cursor:default; outline:none; }}
  .node:hover .box, .node:focus .box {{ stroke:var(--text-secondary); stroke-width:1.6; }}
  #tip {{ position:fixed; max-width:330px; background:var(--box); color:var(--text-primary);
    border:1px solid var(--ring); border-radius:8px; padding:9px 11px; font-size:12px; line-height:1.45;
    box-shadow:0 6px 22px rgba(0,0,0,.16); pointer-events:none; opacity:0; transition:opacity .1s; z-index:9; }}
  table {{ width:100%; border-collapse:collapse; font-size:12px; margin-bottom:10px; }}
  th, td {{ text-align:left; padding:7px 9px; border-bottom:1px solid var(--rule); }}
  th {{ color:var(--text-secondary); font-weight:600; }}
  td:nth-child(2), td:nth-child(3) {{ color:var(--text-secondary); white-space:nowrap; }}
  .note {{ font-size:12px; color:var(--text-secondary); line-height:1.55; margin:10px 0 12px; }}
  .note strong {{ color:var(--text-primary); }}
</style></head>
<body><div class="viz-root"><div class="wrap">

<h1>ike-starter-set — current state</h1>
<p class="sub">Directed: every arrow points from a thing to what depends on it. Snapshot taken 20 Aug 2026.</p>

<div class="stats">{stat_html()}</div>

<section>
  <h2>Build reactor</h2>
  <p class="cap">Layered by dependency, so left-to-right is also build order.</p>
  <div class="legend">{legend_html()}
    <span class="lg-line"><svg width="26" height="8"><line x1="0" y1="4" x2="26" y2="4" class="edge"/></svg>depends on</span>
    <span class="lg-line"><svg width="26" height="8"><line x1="0" y1="4" x2="26" y2="4" class="edge dashed"/></svg>secondary dependency</span>
  </div>
  <svg class="chart" viewBox="0 0 980 356" role="img" aria-label="Maven reactor dependency tree">
  {svg_defs}
{reactor_edges}
{render_nodes(reactor)}
  </svg>
</section>

<section>
  <h2>Document hierarchy</h2>
  <p class="cap">What the guide would assemble from — the live output of the system.</p>
  <svg class="chart" viewBox="0 0 980 160" role="img" aria-label="Chapter hierarchy">
  {svg_defs}
{doc_edges}
{render_nodes(doc_nodes)}
  </svg>
  <p class="note"><strong>This is the one blocker.</strong> <code>ike-guide.adoc</code> was reverted at 15:55 today, taking its
  <code>:chapter-id:</code> and <code>:chapter-root:</code> lines with it, so <code>cql</code> now points at a parent nobody declares.
  <code>ike-hierarchy:index</code> will stop the build with a <em>DanglingParent</em> violation naming both files — which is the
  designed behaviour, not a regression: a chapter that cannot be placed must never quietly disappear from a 22-chapter book.
  Re-adding two lines to the top of the guide clears it.</p>
</section>

<section>
  <h2>Render pipeline</h2>
  <p class="cap">Discovery happens once, on the left. Everything downstream reads the index.</p>
  <svg class="chart" viewBox="0 0 980 96" role="img" aria-label="Render pipeline">
  {svg_defs}
{pipe_edges}
{render_nodes(pipe)}
  </svg>
</section>

<section>
  <h2>Table view</h2>
  <p class="cap">The same state, without relying on colour or position.</p>
  {table_html()}
</section>

</div></div>
<div id="tip" role="tooltip"></div>
<script>
  const tip = document.getElementById('tip');
  for (const n of document.querySelectorAll('.node')) {{
    const show = e => {{
      tip.textContent = n.dataset.tip; tip.style.opacity = 1;
      const r = tip.getBoundingClientRect();
      const b = n.getBoundingClientRect();
      let x = b.left + b.width / 2 - r.width / 2;
      x = Math.max(8, Math.min(x, window.innerWidth - r.width - 8));
      let y = b.top - r.height - 10;
      if (y < 8) y = b.bottom + 10;
      tip.style.left = x + 'px'; tip.style.top = y + 'px';
    }};
    const hide = () => {{ tip.style.opacity = 0; }};
    n.addEventListener('mouseenter', show); n.addEventListener('focus', show);
    n.addEventListener('mouseleave', hide); n.addEventListener('blur', hide);
  }}
</script>
</body></html>'''

open("ike-starter-set-state.html", "w").write(PAGE)
print("wrote ike-starter-set-state.html", len(PAGE), "bytes")
