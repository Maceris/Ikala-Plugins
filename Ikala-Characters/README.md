# Ikala-Characters

Character and animal design for players, NPCs, enemies and animals. Characters
are built from a few fixed body plans with swappable parts, sliders, palettes
and FX, and are animated by authored clips plus procedural layers.

## Building

This plugin uses Gradle to build. To build you will need Ikala-Core downloaded
as well, in the same folder that the Ikala-Plugins folder was downloaded. To
build this you will need to run `./gradlew clean build` from the command
line.

## Mannequins

`tools/blender/mannequin.py` builds a male (2 m) and a female (1.85 m) biped from
MakeHuman's CC0 game bodies, one mesh per body slot, with idle and walk clips,
and exports them as glTF along with each seam's shared edge loop. Their
armature comes from the built-in rig definition, so add bones there first. The
male is the editor's rig reference, and the female's bones point the same ways
at rest, so clips retargeted onto him fit her too. It needs Blender 5 or later
with the MPFB extension (`blender --online-mode --command extension install -s
-e mpfb`) and the MakeHuman system asset pack unpacked into MPFB's user data, and
writes to the asset editor's `test_content` folder, which isn't in git:

```
blender -b --python tools/blender/mannequin.py -- --rig src/main/resources/characters/ikala/rig/biped.json --plan src/main/resources/characters/ikala/bodyPlan/biped.json --out ../../Ikala-Asset-Editor/test_content/characters/mannequin
```

Clothing fitted to one body type is carried to the others through cages: each
mannequin's MakeHuman base mesh, posed and scaled the same way. The two proxies'
vertices don't line up, but the base meshes do, so a garment bound to the male
cage with a Surface Deform modifier follows when the cage takes the female's
shape. `tools/blender/body_cages.py` writes them beside the mannequins (rerun it
whenever they are rebuilt), and `tools/partgen/blender/cage_transfer_check.py`
measures how a test shirt carries over:

```
blender -b --python tools/blender/body_cages.py -- --out ../../Ikala-Asset-Editor/test_content/characters/mannequin
```

## Test clips from Blender

The test animations are the modular RPG kit's clips (System G6, credit optional),
without its characters. `tools/blender/export_rpg.py` exports one of the kit's
characters as glTF, scaled to a 2 m standing height, with every part, clip and
texture; `tools/test_content/clips_only.py` then keeps just its skeleton and
clips, which retarget onto the biped through `ikala:rig_map/modular_rpg_human`:

```
blender -b human_male.blend --python tools/blender/export_rpg.py -- --out rpg_export --name rpg_male
python tools/test_content/clips_only.py rpg_export/rpg_male.glb ../../Ikala-Asset-Editor/test_content/characters/animations/rpg_clips.glb
```

## Images and meshes from the local models

`tools/partgen` makes character parts in reviewed batches (see `partgen.py`), but
its two models can be used on their own too. Run these from `tools/partgen` with
TRELLIS.2's Python (`<trellis>/.venv/Scripts/python.exe`), or any Python with
Pillow. Paths to ComfyUI, TRELLIS.2, Blender and the work folder come from
`config.json`, which holds this computer's folders and isn't committed: copy
`config.example.json` to it and fill it in. Blender can be named just `blender`
when it is on the PATH. Below, `<trellis>` and `<work>` stand for those folders.
Neither model needs the
internet once the models are downloaded: ComfyUI is started with `--offline`, and
`trellis_run.py` only loads models from Hugging Face's local cache unless given
`--online` (needed once, to download them).

An image from a prompt, through SDXL in ComfyUI (started if it isn't running;
about a minute, more with `--count`). `--style` asks for an isolated product
render, which TRELLIS.2 turns into a mesh best; `--sketch drawing.png` steers the
shape with a drawing:

```
python make_image.py "a red wooden treasure chest with iron bands" --style --count 4 --out <work>/scratch/chest
```

More like them later, with the same prompt and settings from the folder's
`prompt.json`, carrying on from the next seed (images are never overwritten):

```
python make_image.py --more <work>/scratch/chest --count 4
```

A textured mesh from an image, through TRELLIS.2 (about a minute to load, then
under a minute to a few minutes per image at the default `--type 512`;
`--type 1024_cascade` gives finer detail, slower). The glb is in a 1 m box, Y up, facing +Z. Several image and glb pairs
can follow, sharing one load:

```
<trellis>/.venv/Scripts/python.exe trellis_run.py --trellis <trellis> <work>/scratch/chest/2000.png <work>/scratch/chest/chest.glb
```

Renders of a mesh from the front, a front corner and the back:

```
blender -b --factory-startup --python blender/render_meshes.py -- <work>/scratch/chest/chest.glb <work>/scratch/chest/views
```

## Generated body parts

partgen briefs can also ask for body parts that replace the mannequin's, like a
mechanical forearm, a skeleton arm or a hooved leg (`tools/partgen/briefs/limbs_1.json`
has a few). A body brief has `"kind": "body"` and lists the body plan `slots` it
fills, which must be neighbors, such as `["upperArm.L", "lowerArm.L", "hand.L"]`
for a whole arm or `["hand.L"]` for a hand. Only the left side is briefed; the
right side will be its mirror, through the slots' `mirrorOf`.

Its outer seams, those of its slots that no other slot in the brief shares, either
weld to the mannequin's seam ring (the default) or are `open`, for a skeletal or
mechanical end that closes on its own; the welded neighbor then shows a cap:

```json
{"id": "skeleton_arm", "kind": "body", "slots": ["upperArm.L", "lowerArm.L", "hand.L"],
 "seams": {"shoulder.L": "open"}, "description": "a bare skeleton arm of old yellowed bone"}
```

`tools/partgen/bodyparts.json` has the rules for each group of slots (arm,
forearm and hand, forearm, hand, leg, lower leg and foot, shin, foot): the prompt,
how hard the mannequin's depth steers, how far the mask grows and the triangle
budget; a brief's `fit` replaces any of them. The images are painted over the
mannequin's render only inside the brief's slots, grown outward but kept off the
rest of the body and cut at the line of each outer seam's ring (from
`<variant>_seams.json` beside the mannequin), so the body the part joins stays as
it is. Images and meshes work as for clothing.

By default (each rule's `isolated`) a body part is painted over a render of its
slots alone, as an object on its own (the rule's `piece` names it), so TRELLIS.2
meshes the part with all its detail instead of as a slice of a whole figure; a
brief's `"fit": {"isolated": false}` paints it over the whole limb instead. TRELLIS
turns such an object however it likes, so the fit tries it turned many ways along
the slots' long axis, keeps the turn whose front outline best matches the image,
and slides it along the limb until it reaches its outer seams.

```
python partgen.py new limbs_1 briefs/limbs_1.json --keep
python partgen.py images limbs_1 --all --count 2
```

Once parts are fitted, `capture` shows each in the real renderer, on both bare
mannequins: front, side, back and a close-up framed on the part, in
`<batch>/<id>/editor/`, beside its Blender previews on the review page. It runs the
asset editor named by `editor` in `config.json` headless, on a script it writes
(`<batch>/captures.iks`), so no window opens; that editor's Ikala-Characters config
must load the content folder under the namespace parts are written to.

```
python partgen.py capture limbs_1
```

The same works for any check: `-Dikala.headless=true` (or a size like
`-Dikala.headless=1024x1024`) renders without a window, `ui.test().saveFrame(path)`
saves the scene without the GUI, and the `characters` script global drives the
editor (`showDesign`, `settle`, `bare`, `wear`, `bothSides`, `still`, `move`, `wings`, `frame`,
`markers`, `worn`; see `EditorScripts`).

## Generated tails, wings and capes

Parts on the body plan's extension chains, like a tail or a wing, are briefed with
`"kind": "extension"`, the `chain` they go on (wings on `wing.L`; the right wing will
be its mirror) and their `length` in meters along the chain. A part pictured on its
own has no size of its own, so the length sets it; left out, it is the rig's own
chain length from `tools/partgen/extensions.json`:

```json
{"id": "fox_tail", "kind": "extension", "chain": "tail", "length": 0.9,
 "description": "a bushy orange fox tail with a white tip"}
```

Its images picture it on its own, steered lightly by the depth of a plain stand-in
along the chain at rest, made the brief's length: a tapered tube for a tail, seen
from the side, and a flat membrane for a wing, seen from behind (the shapes from
`tools/blender/test_extensions.py`; the rule's `view` and `roll` pick the camera). So
it comes out in the chain's pose and proportions, and spreads past the stand-in as
its description asks. `render_guides.py --chain tail --chain-scale 1.2` renders a
stand-in on its own.

A cape is clothing in the plan's `cape` layer: its rule in `layers.json` has
`"view": "back"`, so its images are painted over the mannequin seen from behind,
down past the waist (`maskDown`).

```
python partgen.py new extensions_trial briefs/extensions_trial.json --keep
python partgen.py images extensions_trial --count 4
```

TRELLIS.2 sometimes hangs curtains of thin upright streaks off a tail or wing, the
same seed clean on another run, so their meshes are checked for streak triangles
and made again with the next seed (`trellis_run.py --object`). From a single wing
it often makes a pair, or a whole bat; fitting will have to keep one side.

Fitting either onto the chain isn't built yet: `fit` leaves them with their meshes.

## Previewing characters

In the editor, with a 3D scene open, `character show <file.glb> [clip]` reads a
character from glTF and shows it where the camera looks at the ground, playing
the clip (idle by default). A skeleton with the same bones as a loaded rig, like
the mannequin's, takes that rig's definition. `character hide` removes it.

## Character editor

The editor's main menu lists **Character Editor**, a tool this plugin offers (any
program with the plugin can list it the same way). `character editor` opens or
closes it from the console. It shows a character on its own floor, with an orbit
camera (left drag turns, middle drag pans, the wheel zooms), a part panel, a
slider panel and a clip bar. Its panels are UI specs in
`src/main/resources/ui/character-editor`.

Choosing parts follows the wear rules (`design/WearRules`), which the shown look
in game follows too, so a design made in the editor is always valid:

- The last part put on wins: it takes off what it overlaps, what lists it (or it
  lists) in `conflicts`, and parts sharing one of the plan's `exclusiveTags`, like
  two `cloth` pieces (long coats, capes) or two `backDetail` pieces (wings,
  jetpacks).
- It puts on what it `requires`, and taking that off takes it off too.
- With **Both sides** on, a part's `pair` (the other side's part) goes on and off
  with it; off, sides are chosen alone.
- What a choice took off comes back once nothing is in its way, so switching a
  skeleton arm back to the mannequin's arm restores the forearm and hand.
- Options the body variant's tags don't allow, or whose required parts aren't
  offered, aren't listed. Slots with nothing to offer aren't shown.
- Worn parts hide the layers in `hidesLayers` and the sockets in `hidesSockets`
  (accessories and effects there), and grey out the sliders in `locksSliders`, by
  slider ID or editor group, like a full helmet's `["face"]`.

It switches between the glTF files listed under `editor.characters` in the config,
putting each on a loaded rig: straight on if a rig has its bones, else moved
through a rig map using `editor.reference` as the rig's reference. Until parts
come from definitions, parts are sorted into slots by name, so a modular kit's
heads, hair and armor are each one slot.
