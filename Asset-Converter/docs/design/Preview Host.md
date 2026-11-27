# Preview host

The workbench shows the open file through one `PreviewHost`, in its Viewer panel. The host draws what every viewer
shares: a toolbar with the kind of viewer, the file's name and a Capture button, then the viewer's own controls on
the same line, and the viewer's content below. The properties panel shows the viewer's details. A `Viewer`
supplies only its content, its controls, its properties and, if it can, a capture. Showing a viewer closes the one
before, which releases what it owned, like its texture. Opening a file brings the Viewer panel's tab to the front.

## Viewers

| Viewer | Shows | Notes |
|---|---|---|
| `TextureViewer` | images and DDS textures | channels, mip level, exposure, 3x3 tiling, the pixel under the mouse, Measure |
| `ContainerViewer` | `.ika` containers | header, metadata, sections with checksums, problems; a picked section's bytes in the properties panel |
| `Swatch` | pixels made on the CPU, like a graph node's output | a texture viewer without its toolbar controls; new pixels replace the old ones and keep the view |

The 3D viewer, on preview views, comes with milestone E3b.

## Decisions

- **The container viewer replaced the asset inspector window.** Browsing was already the workbench's job, so
  `.ika` files open from its file list like any other asset, and the sample containers are written from the Files
  panel.
- **Captures are drawn on the CPU, not read back from the screen.** A texture capture shades the image's stored
  pixels the way the view does (channel, exposure, sRGB decoding and encoding, normal rebuild, tiling) and writes an
  8 bit PNG at the image's own size. It doesn't depend on the window's size or zoom, so check scripts get the same
  file every run. Textures whose pixels can't be read back, some DDS formats, can't be captured.
- **Measure is the alpha-weighted average linear color, with its luminance.** sRGB images are decoded first, so the
  number compares with measured albedos (most real materials lie between 0.04 and 0.9). It replaces the albedo
  targets and hover checks the plan dropped: a number to read when wanted, not a judgment.
- **Swatches are linear half floats,** so values above 1 and below the 8 bit step survive, and the view encodes them
  to sRGB to show.
