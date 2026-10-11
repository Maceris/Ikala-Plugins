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

World generation is data driven: KVT files under `mods/<mod>/worldgen/<kind>/` in a data folder,
where the ID comes from the path (`mods/lotomation/worldgen/density/overworld/terrain.kvt` is
`lotomation:overworld/terrain`). The kinds are `noise`, `density`, `parameter`, `biome`,
`block_rules`, `fluids` and `world_type`; types are defined in code (`WorldgenTypes`) and data
configures and combines them. KVT accepts `/* ... */` comments. Chunks are cubic, 16^3, and
generation is a pure function of the seed, the chunk position and the data.

The tools run the same code the game generates with. From this folder:

```bash
./gradlew worldgen --args="check <dataFolder> --blocks <blocks.csv>"
./gradlew worldgen --args="render --data <dataFolder> --world lotomation:overworld --target lotomation:overworld/terrain --plane xy --origin -256,64,0 --size 512,256 --out side.png"
```

Commands: `check` (errors with file and field), `render` (a PNG slice of any noise, density,
parameter, `id#path` node, `biome` or `blocks`), `hash` (chunk content hashes), `trace` (every
node's value at a point), `biome` (why a point has its biome) and `bounds` (every node's range over
a box). Relative paths are from this folder. The same commands work in the running game's console
as `worldgen <command> ...`.

The determinism tests compare a fixed set of chunks against
`src/test/resources/worldgen/golden-hashes.txt`. When generation changes on purpose, regenerate it
with `./gradlew test -Dworldgen.updateGolden=true` and commit the new file.
