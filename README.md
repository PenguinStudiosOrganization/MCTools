# MCTools

Advanced shape generation & building tool for Minecraft builders.

## Project

- **Development team**: PenguinStudios
- **Official website**: https://mcutils.net/
- **Downloads (GitHub Releases)**: https://github.com/PenguinStudiosOrganization/MCTools/releases/
- **Discord**: https://discord.penguinstudios.eu/
- **Version**: 1.3.0

---

## What's New in 1.3.0

### 🤖 AI Structure Generator
Generate entire structures from AI with a single command. Fetches pre-built structures from the MCUtils API and places them in your world with full preview, progress tracking, and undo support.

- **`/mct build <id>`** — Fetch and build an AI-generated structure by its ID
- **API-powered**: Structures are fetched from `mcutils.net` and parsed in real-time
- **Full preview**: Shows a 10-second glass preview before placing the actual blocks (configurable)
- **Block resolution**: Automatically resolves Minecraft block names with multi-layer fallback
- **Progress tracking**: Boss bar with ETA, percentage, and blocks/second during placement
- **Async fetching**: HTTP requests run off the main thread — zero lag during download
- **Safety**: Block count limits, server performance checks, and cooldown support
- **Undo support**: Full `/mct undo` and `//undo` (WorldEdit) integration
- **Configurable**: Enable/disable, custom API URL, and timeout in `config.yml` under `ai-build`

### 🛤️ Path Tools (Road / Bridge / Curve)
A brand-new shovel-based multipoint selection system for generating roads, bridges, and smooth curves along a polyline path. Works like WorldEdit's selection wand but builds an ordered path of 2+ points.

- **Three modes**: Road, Bridge, and Curve — each with its own set of configurable settings.
- **Shovel selection**: Left-click sets Pos1 (resets path), right-click appends Pos2, Pos3, … up to 50 points.
- **Curve engine**: Catmull-Rom and Bézier interpolation with configurable resolution for smooth paths.
- **Road generator**: Generates a road surface with configurable width, materials, border, centerline, slab/stair slopes, terrain adaptation, clearance, and sub-road fill.
- **Bridge generator**: Generates a bridge deck with optional railings, cylindrical support pillars (configurable width/spacing/depth), entry/exit ramps, and auto/fixed height modes.
- **Particle preview**: `FLAME` particles show the sampled curve footprint before committing. `SOUL_FIRE_FLAME` for bridge support positions. `HAPPY_VILLAGER` for selected points.
- **Settings GUI**: Run `/mct set` (no arguments) to open an interactive chest GUI for the active mode. Click items to adjust values — left-click increments, right-click decrements, shift for larger steps. Material settings cycle through curated presets.
- **Full command set**: `/mct tool`, `/mct mode`, `/mct pos`, `/mct set`, `/mct preview`, `/mct generate`, `/mct particles`.
- **Tab completion**: Full contextual tab completion for all path subcommands, setting keys, and setting values.
- **`//wand` integration**: The `//wand` command now offers a **[Path Tool]** option alongside WorldEdit and MCTools Brush.
- **Performance**: Uses the existing `BlockPlacer` queue with adaptive blocks-per-tick. Path length and point count are capped with configurable limits.
- **Safety**: Full undo/redo support, block limit checks, protected area respect, and error messages for all edge cases.

### 🗂️ Schematic System
Full schematic management with preview, paste, and rotation support. Requires WorldEdit as soft dependency.

- **`/mct schematic list`** — Lists all `.schem` / `.schematic` files in the schematics folder with file sizes. Each entry has a clickable **[+]** button to quickly load it.
- **`/mct schematic load <name>`** — Loads a schematic into memory. If another schematic was already loaded, it gets automatically replaced.
- **`/mct schematic info`** — Shows detailed info about the loaded schematic (name, file size, dimensions, volume) with clickable action buttons.
- **`/mct schematic rotate <degrees>`** — Rotates the loaded schematic by the given degrees (must be a multiple of 90). Block states like stairs, logs, and doors are rotated correctly.
- **`/mct schematic unload`** — Unloads the current schematic from memory.
- **`/mct schematic remove <name>`** — Permanently deletes a schematic file from disk.
- **`/mct paste [-a] [-p]`** — Pastes the loaded schematic at your position. Shows a **10-second preview** using colored glass before committing:
  - 🟦 **Light blue glass** = solid/full blocks
  - 🟪 **Magenta glass** = special blocks (slabs, stairs, fences, doors, etc.)
  - Use **`-a`** flag to skip air blocks from the schematic
  - Use **`-p`** flag to skip the preview and paste immediately
  - Flags can be combined: `/mct paste -a -p`
  - Clickable **[Cancel]** button during preview
  - Boss bar countdown timer
  - Supports both `//undo` (WorldEdit) and `/mct undo`

### 🖌️ Improved Heightmap Brush
- More accurate heightmap reading with subtle per-generation variation in block positions and rotation, while keeping the overall shape coherent.

### 🔧 Other Improvements
- **Refreshed message style**: Error messages now use a bold red **✕** icon; prefix spacing around the `│` separator has been tightened for a cleaner look across all message types (info, error, warning, success)
- **Unknown argument handling**: Running `/mct <invalid>` now shows a clear message — *The argument "X" doesn't exist* — with a clickable wiki link and `/mct help` suggestion, instead of a generic "unknown shape" error
- **`/mct help` overhaul**: The help menu now lists every command with correct, up-to-date parameter syntax — including all path tool commands, schematics, gradient shapes, tree generator, and admin utilities
- Full tab completion for all schematic subcommands including rotate degrees (90, 180, 270)
- Smart tab completion for `/mct paste` flags (`-a`, `-p`) — suggests only unused flags
- Smart tab completion for `/mct set <key> <value>` — contextual value suggestions per key type
- `//wand` now shows a choice menu with WorldEdit Wand, MCTools Brush, and Path Tool Shovel

### 🪛 IMPORTANT MESSAGE
- The command **//undo** and **//redo** doesn't work with PATH Generation, will be fixed in **v1.3.1**
---

## Features

- **2D Shapes**: Circles, squares, rectangles, ellipses, polygons, stars, lines, spirals
- **3D Shapes**: Spheres, domes, cylinders, cones, pyramids, arches, torus, walls, helixes, ellipsoids, tubes, capsules
- **Hollow Variants**: All shapes support hollow versions with customizable thickness
- **Gradient Shapes**: Apply color gradients to any shape using hex colors with perceptual color matching (OkLAB, LAB, RGB, HSL interpolation)
- **Procedural Tree Generator**: Generate realistic trees with 10 wood types and customizable parameters (trunk, branches, foliage, roots)
- **Terrain Brush**: Advanced terrain editing with heightmap support, multiple modes (raise, lower, smooth, flatten), and interactive GUI
- **Path Tools**: Shovel-based multipoint selection for generating roads, bridges, and smooth curves along a polyline path with particle preview
- **AI Structure Generator**: Fetch and build AI-generated structures with `/mct build <id>` — full preview, progress tracking, and undo support
- **Schematic System**: Load, rotate, preview and paste `.schem` files with dual-color preview and WorldEdit undo support
- **Preview Mode**: Preview shapes before placing with teleportation
- **Undo/Redo System**: Up to 1000 operations history per player
- **Pause/Resume**: Pause and resume active block placement operations
- **Performance Monitor**: Adaptive block placement that adjusts speed based on TPS and RAM usage
- **Async Block Placement**: No lag during large operations with progress bars and ETA

---

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
/mct g<shape> <#hex1,#hex2,...> <params> [-dir <direction>] [-interp <mode>] [-unique]
```

**Examples:**
```
/mct gsph #ff0000,#0000ff 20              - Red to blue gradient sphere
/mct gcyl #ffd700,#8b0000 10 8 -dir y     - Gold to dark red cylinder (vertical)
/mct gsph #ff0000,#00ff00,#0000ff 15 -dir radial -interp oklab
```

**Options:**
- `-dir <y|x|z|radial>` — Gradient direction (default: y)
- `-interp <oklab|lab|rgb|hsl>` — Color interpolation mode (default: oklab)
- `-unique` — Use only unique blocks (no repeated materials)

### Procedural Tree Command

```
/mct tree <woodtype> [options]
```

**Wood types:** oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, crimson, warped

**Options:** `seed:` `th:` `tr:` `bd:` `fd:` `fr:` `-roots` `-special`

### Schematic Commands

```
/mct schematic list                    - List available schematics with sizes
/mct schematic load <name>             - Load a schematic (auto-unloads previous)
/mct schematic info                    - Info about loaded schematic
/mct schematic rotate <degrees>        - Rotate loaded schematic (90/180/270)
/mct schematic unload                  - Unload current schematic
/mct schematic remove <name>           - Delete a schematic file
/mct paste [-a] [-p]                   - Paste with 10s preview (-a = skip air, -p = no preview)
```

### AI Structure Generator

```
/mct build <id>          - Fetch and build an AI-generated structure by ID
```

### Admin & Utility Commands

```
/mct undo [count]        - Undo operations (up to 1000)
/mct redo [count]        - Redo undone operations
/mct cancel              - Cancel current operation
/mct pause               - Pause current operation
/mct resume              - Resume paused operation
/mct performance         - Show server performance (TPS, MSPT, RAM, historical averages)
/mct center              - Find the center of a nearby structure (200-block radius)
/mct wand                - Get the MCTools Terrain Brush wand
/mct reload              - Reload configuration
/mct help [shape]        - Show help
/mct info                - Plugin information
```

### WorldEdit Integration (if installed)

```
//undo [count]           - Also works for MCTools operations
//redo [count]           - Also works for MCTools operations
//wand                   - Choose between WorldEdit axe, MCTools brush, or Path Tool shovel
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

### Path Tools (Road / Bridge / Curve)

A shovel-based multipoint selection system for generating roads, bridges, and smooth curves along a polyline path. Works like WorldEdit's selection wand but builds an ordered path of 2+ points.

#### Overview & Workflow

1. **Get the tool**: Use `//wand` and pick **[Path Tool]**, or run `/mct tool enable` while holding any shovel.
2. **Select a mode**: `/mct mode road` (or `bridge` / `curve`).
3. **Define a path**: Left-click with the shovel to set Pos1 (resets the path). Right-click to append Pos2, Pos3, etc.
4. **Adjust settings** (optional): `/mct set width 7`, `/mct set material STONE`, etc.
5. **Preview**: `/mct preview on` — particles show the sampled curve and footprint.
6. **Generate**: `/mct generate` — places blocks along the path.
7. **Undo**: `/mct undo` — reverts the last generation.

#### Selection System

| Action | Behavior |
|---|---|
| **Left-click** with shovel on a block | Sets **Pos1** and clears all previously stored points (resets the path) |
| **Right-click** with shovel on a block | Appends a new point (Pos2, Pos3, …) to the end of the path |
| Minimum valid selection | **2 positions** |
| Point ordering | Insertion order — the path connects points sequentially |
| Allowed shovels | Configurable in `config.yml` (default: `WOODEN_SHOVEL`) |

#### Path Tool Commands

```
/mct help path                         - Show path tools help
/mct tool enable|disable               - Enable/disable the shovel selection tool
/mct mode road|bridge|curve            - Set the active generator mode
/mct pos list                          - List all selected positions with coordinates
/mct pos undo                          - Remove the last added position
/mct pos clear                         - Clear all positions
/mct set                               - Open settings GUI for the active mode
/mct set <key> <value>                 - Change a setting via command
/mct preview on|off                    - Toggle particle preview of the sampled path
/mct generate                          - Generate the structure along the path
/mct particles on|off                  - Toggle selection point/segment particles
/mct cancel                            - Cancel an in-progress generation
/mct undo                              - Undo the last generated structure
```

#### Mode-Specific Settings

All settings are changed with `/mct set <key> <value>`. Run `/mct set` with no arguments to see current values.

**Curve Mode** (preview-only — does not place blocks):

| Key | Type | Default | Description |
|---|---|---|---|
| `resolution` | decimal 0.1–2.0 | `0.5` | Sampling density (lower = smoother, more points) |
| `algorithm` | `catmullrom` / `bezier` | `catmullrom` | Interpolation algorithm |

**Road Mode**:

| Key | Type | Default | Description |
|---|---|---|---|
| `width` | integer 1–32 | `5` | Road width in blocks |
| `material` | block name | `STONE_BRICKS` | Main road surface material |
| `border` | block name / `none` | `POLISHED_ANDESITE` | Border material (edges of the road) |
| `centerline` | block name / `none` | `none` | Optional center stripe material |
| `use-slabs` | true/false | `true` | Use slabs for gentle slopes |
| `use-stairs` | true/false | `true` | Use stairs for steeper slopes |
| `terrain-adapt` | true/false | `true` | Adapt road surface to terrain height |
| `clearance` | integer 1–10 | `3` | Blocks of air cleared above the road |
| `fill-below` | integer 0–20 | `4` | Depth of fill material placed below the road |
| `fill-material` | block name | `COBBLESTONE` | Material used for sub-road fill |
| `resolution` | decimal 0.1–2.0 | `0.5` | Curve sampling density |

**Bridge Mode**:

| Key | Type | Default | Description |
|---|---|---|---|
| `width` | integer 1–32 | `5` | Bridge deck width in blocks |
| `deck-material` | block name | `STONE_BRICK_SLAB` | Deck surface material |
| `railings` | true/false | `true` | Generate railings on both sides |
| `railing-material` | block name | `STONE_BRICK_WALL` | Railing block type |
| `supports` | true/false | `true` | Generate support pillars to the ground |
| `support-material` | block name | `STONE_BRICKS` | Pillar block type |
| `support-spacing` | integer 3–50 | `8` | Distance between support pillars |
| `support-width` | integer 1–10 | `3` | Pillar radius — cylindrical cross-section (higher = thicker pillars) |
| `support-max-depth` | integer 1–128 | `40` | Maximum pillar depth (stops if no ground found) |
| `height-mode` | `auto` / `fixed` | `auto` | `auto` = follow control point Y; `fixed` = flat deck |
| `ramps` | true/false | `true` | Generate entry/exit ramps using stairs |
| `ramp-material` | block name | `STONE_BRICK_STAIRS` | Ramp stair material |
| `resolution` | decimal 0.1–2.0 | `0.5` | Curve sampling density |

#### Settings GUI

Running `/mct set` with **no arguments** opens an interactive chest inventory for the active mode. Each setting is represented by a clickable item:

| GUI | Size | Color Accent |
|---|---|---|
| **Road Settings** | 54 slots (6 rows) | Orange |
| **Bridge Settings** | 54 slots (6 rows) | Light Blue |
| **Curve Settings** | 27 slots (3 rows) | Magenta |

**Interaction:**
- **Left-click** → Increment value / cycle material forward / toggle boolean ON
- **Right-click** → Decrement value / cycle material backward / toggle boolean OFF / set to `none` (for optional materials)
- **Shift-click** → Larger step for numeric values (e.g. ±2 for width, ±10 for max-depth)

Material settings cycle through curated presets of commonly used Minecraft blocks (stone variants, wood, deepslate, etc.). Boolean settings display as green (ON) or gray (OFF) dye. Numeric values show a visual progress bar in the item lore.

> **Note:** You can still use `/mct set <key> <value>` for precise values or materials not in the preset cycle. The GUI and commands read/write the same session data.

#### Examples

```bash
# Basic road workflow
/mct tool enable
/mct mode road
# Left-click block A, right-click blocks B, C, D with shovel
/mct set width 7
/mct set material SMOOTH_STONE
/mct preview on
/mct generate

# Bridge across a valley
/mct mode bridge
# Left-click start, right-click waypoints, right-click end
/mct set deck-material OAK_SLAB
/mct set railings true
/mct set railing-material OAK_FENCE
/mct set supports true
/mct set support-spacing 6
/mct preview on
/mct generate

# Preview a smooth curve (no block placement)
/mct mode curve
# Select 4+ points with shovel
/mct set resolution 0.3
/mct set algorithm catmullrom
/mct preview on
```

#### Error Messages & Behavior Rules

| Condition | Message |
|---|---|
| `/mct preview on` or `/mct generate` or `/mct set ...` without a mode selected | `Select a mode first: /mct mode road\|bridge\|curve` |
| `/mct generate` with fewer than 2 points | `Not enough points. Select at least 2 positions with the shovel.` |
| `/mct generate` with points in different worlds | `All positions must be in the same world.` |
| `/mct generate` in curve mode | `Curve mode is preview-only. Switch to road or bridge to generate blocks.` |
| Path exceeds `max-path-length` | `Path too long (X blocks). Maximum allowed: Y. Remove points or increase the limit.` |
| Points exceed `max-points` | `Maximum points reached (50). Remove a point with /mct pos undo or clear with /mct pos clear.` |
| Block count exceeds `max-blocks` | `Operation would place X blocks (max: Y)` |
| Generation already in progress | `A generation is already in progress. Use /mct cancel first.` |

All messages use the plugin's standard `MessageUtil` prefix and color scheme (green info, red errors, yellow warnings).

#### Particles & Visual Feedback

- **Selection particles** (`HAPPY_VILLAGER`): Shown at each selected point and along line segments between consecutive points. Refreshed automatically when points are added/removed.
- **Preview particles** (`FLAME`): Shown along the sampled curve path when `/mct preview on` is active. For bridge mode, support pillar positions use `SOUL_FIRE_FLAME`.
- Particles are only visible to the selecting player by default.
- Particle refresh interval: 10 ticks (selection), 20 ticks (preview).
- Maximum render distance: 128 blocks from the player.
- Preview is automatically downsampled if the sampled path exceeds 5,000 points.

#### Performance & Safety

- Block placement uses the existing `BlockPlacer` queue/batching system (adaptive blocks-per-tick based on TPS/RAM).
- Path length is capped at `max-path-length` (default: 2,000 blocks) unless the player has `mctools.path.bypass.limit`.
- Maximum control points capped at `max-points` (default: 50).
- Total block count is checked against the global `max-blocks` limit before generation starts.
- Liquids and air are handled by the generator (roads fill below, bridges span over).
- Protected areas: if another plugin cancels the `BlockPlaceEvent`, those blocks are skipped.
- Full undo/redo support via `/mct undo` and `//undo` (WorldEdit).

---

## Permissions

| Permission | Description |
|---|---|
| `mctools.use` | Basic usage |
| `mctools.admin` | Admin commands (reload, schematics) |
| `mctools.bypass.limit` | Bypass block limits |
| `mctools.shape.*` | All shapes |
| `mctools.brush` | Terrain brush |
| `mctools.center` | Center detection |
| `mctools.gradient` | Gradient shapes |
| `mctools.path.use` | Use path tools (selection, mode, settings, preview) |
| `mctools.path.generate` | Generate road/bridge structures |
| `mctools.path.bypass.limit` | Bypass max-points and max-path-length limits |

## Configuration

Edit `config.yml` to customize:
- Maximum radius/height/thickness
- Maximum blocks per operation
- Cooldown between commands
- Async placement settings
- Preview duration and block
- Sound and particle effects
- Brush defaults (size, intensity, max height, block)
- Path tool: allowed shovel materials, max points, max path length
- Performance monitor thresholds
- Boss bar settings

## Installation

1. Download the latest release from [GitHub Releases](https://github.com/PenguinStudiosOrganization/MCTools/releases/)
2. Place the JAR in your `plugins` folder
3. (Optional) Install [WorldEdit](https://dev.bukkit.org/projects/worldedit) for schematic support and `//undo`
4. Restart the server
5. Place `.schem` files in `plugins/MCTools/schematics/` for schematic support
6. Configure `config.yml` as needed

## Requirements

- Minecraft 1.20+
- Paper/Spigot server
- WorldEdit (optional, for schematics and `//undo` integration)

## License

MIT License - See LICENSE file for details.
