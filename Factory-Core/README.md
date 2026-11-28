# Factory-Core

The core components of a factory game.

## Building

This plugin uses Gradle to build. To build you will need Ikala-Core downloaded
as well, in the same folder that the Ikala-Plugins folder was downloaded. To 
build this you will need to run `./gradlew clean build` from the command
line.

The KVT parser is generated with antlr. See `docs` for the g4 files, when antlr is regenerated
the scripts will need to be regenerated like

```bash
java -jar antlr-4.13.2-complete.jar -Dlanguage=Java KVTLexer.g4 KVTParser.g4
```

## World generation

World generation is data driven: KVT files under `worldgen/<kind>/` in a content folder (see
Content below), where the ID comes from the path (`worldgen/density/overworld/terrain.kvt` in the
Lotomation plugin's data folder is `lotomation:overworld/terrain`). The kinds are `noise`, `density`, `parameter`, `biome`,
`block_rules`, `fluids` and `world_type`; types are defined in code (`WorldgenTypes`) and data
configures and combines them. KVT accepts `/* ... */` comments. Chunks are cubic, 16^3, and
generation is a pure function of the seed, the chunk position and the data.

Every type is registered with a `Schema` of its fields (Ikala-Core's `util.schema`), declared
beside its factory: each field's kind, default, range and meaning. The compiler warns about an
object's unknown fields against its type's schema before the factory reads it, and the kinds the
compiler builds itself (parameters, biomes, block rules, features and the rest) declare theirs in
`WorldgenSchemas`, whose `root(kind)` is a whole file as one field, for editors. Fields can be
nested objects, lists, maps with free keys, typed objects whose `type` picks their schema from a
registry (so a mod's types have theirs), and unions, like a density value: a number, a density
file's ID, or a node. A mod registering a type without a schema still works; its fields just go
unchecked.

The tools run the same code the game generates with. From this folder:

```bash
./gradlew worldgen --args="check ../../Lotomation/plugins/Lotomation/data"
./gradlew worldgen --args="render --data <dataFolder> --world lotomation:overworld --target lotomation:overworld/terrain --plane xy --origin -256,64,0 --size 512,256 --out side.png"
```

Commands: `check` (errors with file and field), `render` (a PNG slice of any noise, density,
parameter, `id#path` node, `biome` or `blocks`), `hash` (chunk content hashes), `trace` (every
node's value at a point), `biome` (why a point has its biome) and `bounds` (every node's range over
a box). Relative paths are from this folder. Blocks are checked against the blocks the data folders
define. The same commands work in the running game's console as `worldgen <command> ...`, where
the data folders default to the installed plugins' content folders.

## Content

Factory-Core is the engine and ships no content. Content comes from the data folders of the
plugins that depend on it, such as the base game's `Lotomation` plugin (`ContentFolders`): a
plugin's namespace is its name in lower case, its own files sit directly in its data folder by
kind, and `overrides/<namespace>/` replaces another plugin's files. Folders load in dependency
order, and a later file with the same ID wins.

The definitions are one KVT file each, loaded in this order by `DefinitionLoader`:

- `tags/<name>.kvt`: `{parent:"cold"}`, or `{}`
- `materials/<name>.kvt`: `{parent:"metal"}` or `{tags:[T;"solid"]}`
- `blocks/<id>.kvt`: `{material:"wood", tags:[T;"solid"], item:true, collision:"full"}`, every key
  optional (`item` defaults to true, `collision` to `full`)
- `items/<id>.kvt`: `{material:"bronze", tags:[T;...]}`

Blocks and items are namespaced, `lotomation:stone`. Tag and gameplay material names are shared by
every plugin, so mods agree on what `solid` means. The server, the client and the tools all read
them the same way.

Each kind's fields are declared once, in `ContentSchemas`, as a `Schema` (Ikala-Core's
`util.schema`): each field's name, kind, default, range and meaning. The readers read through them
(`KvtSchemas`), so a value of the wrong kind or out of range is an error naming the field, and an
unknown field is a warning. Block looks (`looks/block`) and water looks (`looks/water`) are read by
Factory-Client but declared here, where the asset editor, which doesn't load the client, makes its
forms from the same schemas. `KvtSchemas.write` writes a file back with only the values that differ
from their defaults, keeping its leading comment, its other keys and the number types it used;
`KvtSchemas.edit` changes an existing file in place, through `KvtText`, which knows where each value
is in the text, so only the changed values' text changes and hand-laid-out files keep their layout.

The determinism tests compare a fixed set of chunks against
`src/test/resources/worldgen/golden-hashes.txt`. When generation changes on purpose, regenerate it
with `./gradlew test -Dworldgen.updateGolden=true` and commit the new file.
