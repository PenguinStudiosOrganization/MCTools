# MCTools

Advanced shape generation tool for Minecraft builders.

## Project

- **Development team**: PenguinStudios
- **Official website**: https://mcutils.net/
- **Downloads (GitHub Releases)**: https://github.com/PenguinStudiosOrganization/MCTools/releases/tag/Release

## Features

- **2D Shapes**: Circles, squares, rectangles, ellipses, polygons, stars, lines, spirals
- **3D Shapes**: Spheres, domes, cylinders, cones, pyramids, arches, torus, walls, helixes
- **Hollow variants**: All shapes support hollow versions with customizable thickness
- **Terrain Brush**: Advanced terrain editing with heightmap support
- **Preview Mode**: Preview shapes before placing with teleportation
- **Undo/Redo System**: Up to 1000 operations history
- **Async Block Placement**: No lag during large operations

## Commands

### Shape Commands

```
/mct cir <block> <radius>           - Filled circle
/mct hcir <block> <radius> <thick>  - Hollow circle
/mct sq <block> <size>              - Filled square
/mct hsq <block> <size> <thick>     - Hollow square
/mct sph <block> <radius>           - Filled sphere
/mct hsph <block> <radius> <thick>  - Hollow sphere
/mct cyl <block> <height> <radius>  - Cylinder
/mct dome <block> <radius>          - Dome
/mct cone <block> <height> <radius> - Cone
/mct pyr <block> <height> <radius>  - Pyramid
/mct tor <block> <major> <minor>    - Torus
/mct arch <block> <leg> <r> <width> - Arch
/mct wall <block> <w> <h> <thick>   - Wall
/mct hel <block> <h> <r> <turns> <t>- Helix
```

### Admin Commands

```
/mct undo [count]  - Undo operations (up to 1000)
/mct redo [count]  - Redo undone operations
/mct cancel        - Cancel current operation
/mct reload        - Reload configuration
/mct help [shape]  - Show help
/mct info          - Plugin information
```

### Terrain Brush

```
/mcbrush           - Open brush GUI
/mcbrush size <n>  - Set brush size
/mcbrush heightmap - Select heightmap
```

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
