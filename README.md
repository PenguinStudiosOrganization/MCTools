# MCTools

Advanced shape generation tool for Minecraft builders.

## Project

- **Development team**: PenguinStudios
- **Official website**: https://mcutils.net/
- **Downloads (GitHub Releases)**: https://github.com/PenguinStudiosOrganization/MCTools/releases/tag/Release

## Features

- **2D Shapes**: Circles, squares, rectangles, ellipses, polygons, stars, lines, spirals
- **3D Shapes**: Spheres, domes, cylinders, cones, pyramids, arches, torus, walls, helixes, ellipsoids, tubes, capsules
- **Hollow Variants**: All shapes support hollow versions with customizable thickness
- **Gradient Shapes**: Apply color gradients to any shape using hex colors with perceptual color matching (OkLAB, LAB, RGB, HSL interpolation)
- **Procedural Tree Generator**: Generate realistic trees with 10 wood types and customizable parameters (trunk, branches, foliage, roots)
- **Terrain Brush**: Advanced terrain editing with heightmap support, multiple modes (raise, lower, smooth, flatten), and interactive GUI
- **Preview Mode**: Preview shapes before placing with teleportation
- **Undo/Redo System**: Up to 1000 operations history per player
- **Pause/Resume**: Pause and resume active block placement operations
- **Performance Monitor**: Adaptive block placement that adjusts speed based on TPS and RAM usage
- **Async Block Placement**: No lag during large operations with progress bars and ETA

## Commands

### 2D Shape Commands

```
/mct cir <block> <radius>              - Filled circle
/mct hcir <block> <radius> <thick>     - Hollow circle
/mct sq <block> <size>                 - Filled square
/mct hsq <block> <size> <thick>        - Hollow square
/mct rect <block> <width> <length>     - Rectangle
/mct hrect <block> <w> <l> <thick>     - Hollow rectangle
/mct ell <block> <rx> <rz>             - Ellipse
/mct hell <block> <rx> <rz> <thick>    - Hollow ellipse
/mct poly <block> <radius> <sides>     - Regular polygon (3-12 sides)
/mct hpoly <block> <r> <sides> <thick> - Hollow polygon
/mct star <block> <radius>             - Star
/mct line <block> <length> <thick>     - Line
/mct spi <block> <radius> <turns>      - Spiral
```

### 3D Shape Commands

```
/mct sph <block> <radius>              - Filled sphere
/mct hsph <block> <radius> <thick>     - Hollow sphere
/mct dome <block> <radius>             - Dome
/mct hdome <block> <radius> <thick>    - Hollow dome
/mct cyl <block> <height> <radius>     - Cylinder
/mct hcyl <block> <h> <r> <thick>      - Hollow cylinder
/mct cone <block> <height> <radius>    - Cone
/mct hcone <block> <h> <r> <thick>     - Hollow cone
/mct pyr <block> <height> <radius>     - Pyramid
/mct hpyr <block> <h> <r> <thick>      - Hollow pyramid
/mct tor <block> <major> <minor>       - Torus
/mct htor <block> <maj> <min> <thick>  - Hollow torus
/mct arch <block> <leg> <r> <width>    - Arch
/mct harch <block> <leg> <r> <w> <t>   - Hollow arch
/mct wall <block> <w> <h> <thick>      - Wall
/mct hel <block> <h> <r> <turns> <t>   - Helix
/mct ellipsoid <block> <rx> <ry> <rz>  - Ellipsoid
/mct hellipsoid <b> <rx> <ry> <rz> <t> - Hollow ellipsoid
/mct tube <block> <h> <outer> <inner>  - Tube (pipe)
/mct capsule <block> <height> <radius> - Capsule (pill shape)
/mct hcapsule <b> <h> <r> <thick>      - Hollow capsule
```

### Gradient Shape Commands

Apply color gradients to any shape using hex colors. Prefix any shape command with `g`:

```
/mct g<shape> <hex1> <hex2> [hex3...] <params> [-dir <direction>] [-interp <mode>] [-unique]
```

**Examples:**
```
/mct gcir #FF0000 #0000FF 10           - Red to blue gradient circle
/mct gsph #FF0000 #00FF00 #0000FF 15   - RGB gradient sphere
/mct gcyl #FFD700 #8B0000 10 8 -dir y  - Gold to dark red cylinder (vertical)
```

**Options:**
- `-dir <y|x|z|radial>` — Gradient direction (default: y)
- `-interp <oklab|lab|rgb|hsl>` — Color interpolation mode (default: oklab)
- `-unique` — Use only unique blocks (no repeated materials)

### Procedural Tree Command

```
/mct tree <woodtype> [height] [radius] [seed]
```

**Wood types:** oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped

### Admin & Utility Commands

```
/mct undo [count]        - Undo operations (up to 1000)
/mct redo [count]        - Redo undone operations
/mct cancel              - Cancel current operation
/mct pause               - Pause current operation
/mct resume              - Resume paused operation
/mct performance         - Show server performance (10s, 1m, 10m, 1h) and calculate blocks/sec
/mct center              - Find the center of a shape within a 200-block radius (experimental)
/mct wand                - Get the MCTools Terrain Generator wand/brush
/mct reload              - Reload configuration
/mct help [shape]        - Show help
/mct info                - Plugin information
```

### WorldEdit Integration (if installed)

```
/* WorldEdit undo/redo also work for MCTools operations */
//undo [count]            - Undo
//redo [count]            - Redo
//wand                   - Prompts you to choose between the MCTools wand or the WorldEdit axe
```

### Terrain Brush

Use a **bamboo** item as brush tool. Left-click opens the GUI, right-click applies the brush.

```
/mcbrush                        - Open brush GUI
/mcbrush toggle|on|off          - Toggle brush
/mcbrush size <n>               - Set brush size
/mcbrush intensity <1-100>      - Set brush intensity
/mcbrush height <n>             - Set max height
/mcbrush block <block>          - Set block type
/mcbrush heightmap <name>       - Set heightmap
/mcbrush mode <mode>            - Set mode (raise, lower, smooth, flatten)
/mcbrush autosmooth <on|off>    - Toggle auto-smooth
/mcbrush smoothstrength <1-5>   - Set smooth strength
/mcbrush list                   - List available heightmaps
/mcbrush reload                 - Reload heightmaps
/mcbrush info                   - Show current settings
/mcbrush help                   - Show help
```

**Brush GUI features:**
- Interactive heightmap selector with ASCII preview
- Block selector with paginated display
- Configurable auto-rotation and circular mask
- Real-time preview mode

## Permissions

- `mctools.use` - Basic usage
- `mctools.admin` - Admin commands (reload)
- `mctools.bypass.limit` - Bypass block limits
- `mctools.shape.*` - All shapes

## Configuration

Edit `config.yml` to customize:
- Maximum radius/height
- Maximum blocks per operation
- Cooldown between commands
- Async placement settings
- Preview duration and block
- Sound and particle effects
- Brush defaults (size, intensity, max height, block)
- Performance monitor thresholds

## Installation

1. Download the latest release
2. Place the JAR in your `plugins` folder
3. Restart the server
4. Configure `config.yml` as needed

## Requirements

- Minecraft 1.20+
- Paper/Spigot server

## License

MIT License - See LICENSE file for details.
