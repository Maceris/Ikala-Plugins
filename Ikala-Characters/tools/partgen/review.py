"""The review page for a partgen batch: keep or drop briefs, pick a concept image, and keep or
drop meshes and fitted parts. Every click is written to the batch's manifest straight away, where
the next stage's command reads it. partgen.py review <batch> starts it; stop it with Ctrl+C.
"""
import http.server
import json
import pathlib
import urllib.parse

MARKS = {"brief": ("keep", "drop", None), "mesh": ("keep", "drop", None),
         "part": ("keep", "drop", None)}

PAGE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Partgen review</title>
<style>
:root { --bg: #f4f4f2; --card: #fff; --text: #222; --muted: #6b6b6b; --line: #dcdcd8;
        --keep: #2f7d4f; --drop: #b23b3b; --pick: #2b62c4; }
@media (prefers-color-scheme: dark) {
  :root { --bg: #1b1c1e; --card: #26282b; --text: #e8e8e6; --muted: #9a9a96; --line: #3a3c40;
          --keep: #5bb784; --drop: #e07070; --pick: #6f9cf0; } }
body { margin: 0; background: var(--bg); color: var(--text); font: 14px/1.4 system-ui, sans-serif; }
header { position: sticky; top: 0; background: var(--bg); border-bottom: 1px solid var(--line);
         padding: 10px 16px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
header h1 { font-size: 16px; margin: 0 12px 0 0; }
.tab { border: 1px solid var(--line); background: var(--card); color: var(--text); border-radius: 6px;
       padding: 5px 10px; cursor: pointer; }
.tab.on { border-color: var(--pick); color: var(--pick); }
main { padding: 16px; display: grid; gap: 12px;
       grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 12px; }
.card.keep { border-color: var(--keep); } .card.drop { opacity: .5; }
.id { font-weight: 600; } .meta { color: var(--muted); font-size: 12px; margin: 2px 0 8px; }
.note { font-size: 12px; color: var(--muted); font-style: italic; }
.imgs { display: grid; grid-template-columns: repeat(auto-fill, minmax(90px, 1fr)); gap: 6px; }
.imgs.views { grid-template-columns: repeat(3, 1fr); }
.imgs img { width: 100%; border-radius: 4px; border: 2px solid transparent; display: block;
            background: #ccc; }
.pickable img { cursor: pointer; } .picked img { border-color: var(--pick); }
.buttons { display: flex; gap: 6px; margin-top: 8px; }
button.mark { flex: 1; border: 1px solid var(--line); background: transparent; color: var(--text);
              border-radius: 6px; padding: 5px; cursor: pointer; }
button.keep.on { background: var(--keep); color: #fff; border-color: var(--keep); }
button.drop.on { background: var(--drop); color: #fff; border-color: var(--drop); }
.empty { color: var(--muted); }
</style></head><body>
<header><h1 id="title">Partgen</h1></header>
<main id="cards"></main>
<script>
const STAGES = [
  ["briefs", "Briefs", b => true],
  ["images", "Images", b => b.review.brief === "keep" && b.images.length],
  ["meshes", "Meshes", b => b.mesh],
  ["parts", "Parts", b => b.part],
];
let manifest = null, shown = "", stage = localStorage.getItem("partgen.stage") || "briefs";

async function mark(id, field, value) {
  await fetch("/mark", {method: "POST", body: JSON.stringify({id, field, value})});
  await load(true);
}

function buttons(b, field) {
  const div = document.createElement("div");
  div.className = "buttons";
  for (const value of ["keep", "drop"]) {
    const button = document.createElement("button");
    button.className = "mark " + value + (b.review[field] === value ? " on" : "");
    button.textContent = value === "keep" ? "Keep" : "Drop";
    button.onclick = () => mark(b.id, field, b.review[field] === value ? null : value);
    div.append(button);
  }
  return div;
}

function image(path, stamp) {
  const img = document.createElement("img");
  img.loading = "lazy";
  img.src = "/file?path=" + encodeURIComponent(path) + "&t=" + stamp;
  return img;
}

function card(b) {
  const div = document.createElement("div");
  const field = {briefs: "brief", meshes: "mesh", parts: "part"}[stage];
  div.className = "card " + (field ? (b.review[field] || "") : "");
  div.innerHTML = `<div class="id"></div><div class="meta"></div>`;
  div.querySelector(".id").textContent = b.id;
  div.querySelector(".meta").textContent = `${b.kind} on ${b.socket}` +
      (b.tags && b.tags.length ? " · " + b.tags.join(", ") : "");
  const text = document.createElement("div");
  text.textContent = b.description;
  div.append(text);
  const note = (b.notes || {})[stage];
  if (note && typeof note === "string") {
    const n = document.createElement("div"); n.className = "note"; n.textContent = note;
    div.append(n);
  }
  if (stage === "images") {
    const grid = document.createElement("div"); grid.className = "imgs";
    const notes = (b.notes || {}).images || {};
    for (const seed of b.images) {
      const cell = document.createElement("div");
      cell.className = "pickable" + (b.review.pick === seed ? " picked" : "");
      cell.append(image(`${b.id}/images/${seed}.png`, ""));
      const caption = document.createElement("div"); caption.className = "note";
      caption.textContent = seed + (notes[seed] ? " · " + notes[seed] : "");
      cell.append(caption);
      cell.onclick = () => mark(b.id, "pick", b.review.pick === seed ? null : seed);
      grid.append(cell);
    }
    div.append(grid);
  } else if (stage === "meshes" || stage === "parts") {
    const grid = document.createElement("div"); grid.className = "imgs views";
    for (let i = 0; i < 3; i++) {
      grid.append(stage === "meshes" ? image(`${b.id}/${b.mesh.views}/view_${i}.png`, b.mesh.file)
                                     : image(`${b.id}/fit/preview_${i}.png`, b.part));
    }
    div.append(grid);
  }
  if (field) div.append(buttons(b, field));
  return div;
}

function render() {
  document.getElementById("title").textContent = "Partgen · " + manifest.batch;
  const header = document.querySelector("header");
  header.querySelectorAll(".tab").forEach(t => t.remove());
  for (const [key, label, show] of STAGES) {
    const tab = document.createElement("button");
    tab.className = "tab" + (key === stage ? " on" : "");
    tab.textContent = `${label} (${manifest.briefs.filter(show).length})`;
    tab.onclick = () => { stage = key; localStorage.setItem("partgen.stage", key); render(); };
    header.append(tab);
  }
  const cards = document.getElementById("cards");
  cards.replaceChildren();
  const show = STAGES.find(s => s[0] === stage)[2];
  const list = manifest.briefs.filter(show);
  if (!list.length) cards.innerHTML = `<div class="empty">Nothing at this stage yet.</div>`;
  for (const b of list) cards.append(card(b));
}

async function load(force) {
  const text = await (await fetch("/manifest")).text();
  if (force || text !== shown) { shown = text; manifest = JSON.parse(text); render(); }
}
load(true);
setInterval(load, 4000);
</script></body></html>
"""


def serve(batch, port):
    folder = batch.folder.resolve()

    class Handler(http.server.BaseHTTPRequestHandler):
        def send(self, code, body, kind):
            self.send_response(code)
            self.send_header("Content-Type", kind)
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            url = urllib.parse.urlparse(self.path)
            if url.path == "/":
                self.send(200, PAGE.encode("utf8"), "text/html; charset=utf-8")
            elif url.path == "/manifest":
                self.send(200, batch.path.read_bytes(), "application/json")
            elif url.path == "/file":
                relative = urllib.parse.parse_qs(url.query).get("path", [""])[0]
                path = (folder / relative).resolve()
                if folder not in path.parents or not path.is_file():
                    self.send(404, b"not found", "text/plain")
                else:
                    self.send(200, path.read_bytes(), "image/png")
            else:
                self.send(404, b"not found", "text/plain")

        def do_POST(self):
            if self.path != "/mark":
                self.send(404, b"not found", "text/plain")
                return
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            field, value = body["field"], body["value"]
            if field == "pick":
                valid = value is None or isinstance(value, int)
            else:
                valid = value in MARKS.get(field, ())
            if not valid:
                self.send(400, b"bad mark", "text/plain")
                return

            def change(entry):
                entry["review"][field] = value

            batch.update(body["id"], change)
            self.send(200, b"{}", "application/json")

        def log_message(self, *args):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print("Reviewing %s at http://127.0.0.1:%d (Ctrl+C to stop)" % (batch.folder, port), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
