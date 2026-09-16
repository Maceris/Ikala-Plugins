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