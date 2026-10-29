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

## Test characters from Blender

`tools/blender/export_rpg.py` exports one of the modular RPG characters (System G6,
credit optional) as glTF, scaled to a 2 m standing height, with every part,
clip and texture:

```
blender -b human_male.blend --python tools/blender/export_rpg.py -- --out ../../Ikala-Asset-Editor/test_content/characters/rpg_human --name rpg_male
```

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

It switches between the glTF files listed under `editor.characters` in the config,
putting each on a loaded rig: straight on if a rig has its bones, else moved
through a rig map using `editor.reference` as the rig's reference. Until parts
come from definitions, parts are sorted into slots by name, so the RPG
characters' heads, hair and armor are each one slot.
