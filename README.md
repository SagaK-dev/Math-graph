# GeoSketch

GeoSketch is an offline Java desktop app for creating geometry diagrams and function graphs. It is designed for math worksheets and exam questions.

## Features

- Draw points, lines, circles, polygons, and regular polygons (3–60 sides).
- Construct fixed angles and choose whether to show angle labels.
- Edit line colors, widths, dashed styles, and hatched regions.
- Show or hide axes, tick labels, and the grid.
- Crop and export diagrams as PNG images, with optional figure captions.
- Save and reopen editable `.gsk` projects.

## Requirements

Java 17 or later. No external libraries or account are required.

## Build and run

**Windows:** Run `build_windows.bat`.

**macOS / Linux:** Run `sh build_unix.sh`.

Then start the app:

```bash
java -jar GeoSketch.jar
```

Export a saved project to PNG:

```bash
java -jar GeoSketch.jar --export input.gsk output.png 1600 1000
```

## Tests

```bash
java -jar GeoSketch.jar --self-test smoke.png
java -jar GeoSketch.jar --feature-test features.png
java -jar GeoSketch.jar --geometry-test geometry.png
```

GeoSketch is an independent project and is not affiliated with GeoGebra. It does not support GeoGebra's `.ggb` format.
