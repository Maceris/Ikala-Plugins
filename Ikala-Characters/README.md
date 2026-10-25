# Ikala-Characters

Character and animal design for players, NPCs, enemies and animals. Characters
are built from a few fixed body plans with swappable parts, sliders, palettes
and FX, and are animated by authored clips plus procedural layers.

## Building

This plugin uses Gradle to build. To build you will need Ikala-Core downloaded
as well, in the same folder that the Ikala-Plugins folder was downloaded. To
build this you will need to run `./gradlew clean build` from the command
line.

## Test mannequin

`tools/blender/mannequin.py` builds a plain 2 m biped, one mesh per body slot,
with idle and walk clips, and exports it as glTF. Its armature comes from the
built-in rig definition, so add bones there first. Run it with Blender 3.3 or
later, writing to the asset editor's `test_content` folder, which isn't in git:

```
blender -b --factory-startup --python tools/blender/mannequin.py -- --rig src/main/resources/characters/ikala/rig/biped.json --out ../../Ikala-Asset-Editor/test_content/characters/mannequin
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
