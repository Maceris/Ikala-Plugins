# Ikala-Graphics

This plugin adds graphics functionality backed by LWJGL libraries.

## Graphics Manager

The `GraphicsManager` class provides utilities for managing graphical 
windows.


**Icon**

The icon defaults to `textures/game_icon.png`, but can be changed in the configuration.

**Filters**
Filters can be applied to the graphics. The folder where the filters are found defaults to `shaders/filters` but can be configured.
These should have a uniform `sampler2D screenTexture` where the texture is passed in to be modified.
