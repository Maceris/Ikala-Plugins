# Ikala-World

A plugin that handles maps and the world.


## Map Format

This plugin uses the [Tiled Map Editor](https://www.mapeditor.org/) for a standard map format. We use some custom classes in the maps so that most of what we need in a level can be encoded in the maps.


### Custom Types

Custom types are listed in bold, with their properties and corresponding types listed below.

**Vector3f**
* X (float)
* Y (float)
* Z (float)

**Point Light**
* Color (color)
* Position (float)

**Spot Light**
* Cone Direction (Vector3f)
* Cutoff Angle (float)
* Point Light (Point Light)

**Directional Light**
* Color (color)
* Direction (Vector3f)
* Intensity (float)
