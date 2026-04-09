# MCTools

Advanced shape generation & building tool for Minecraft builders.

## Project

- **Development team**: PenguinStudios
- **Official website**: https://penguinstudios.eu/
- **ShapeGen website**: https://shapegen.penguinstudios.eu
- **Downloads (BuiltByBit)**: https://builtbybit.com/resources/mctools.102917/
- **Discord**: https://discord.penguinstudios.eu/
- **Version**: 1.0.0

---

## Features

- **2D Shapes**: Circles, squares, rectangles, ellipses, polygons, stars, lines, spirals
- **3D Shapes**: Spheres, domes, cylinders, cones, pyramids, arches, torus, walls, helixes, ellipsoids, tubes, capsules
- **Hollow Variants**: Any hollow-capable shape supports `-h [thickness]` — no separate command needed
- **Gradient Shapes**: Apply color gradients to any shape via `/mct gradient`, with perceptual color matching (OkLAB, LAB, RGB, HSL)
- **Procedural Tree Generator**: Realistic trees with 10 wood types and fully customizable parameters
- **Terrain Brush**: Advanced terrain editing with heightmap support, multiple modes, and interactive GUI
- **Path Tools**: Shovel-based multipoint selection for roads, bridges, and curve previews with particles
- **Schematic System**: Load, rotate, preview and paste `.schem` files with WorldEdit undo support
- **Preview Mode**: Preview shapes before placing with teleportation
- **Undo/Redo System**: Up to 1000 operations history per player
- **Pause/Resume**: Pause and resume active block placement
- **Performance Monitor**: Adaptive placement that adjusts to TPS and RAM
- **Async Block Placement**: No lag during large operations — progress bars and ETA

---

## Command Structure

MCTools uses a grouped command structure. All shape, gradient, and path operations live under their own subcommand, making tab completion predictable and the command tree easy to navigate.

```
/mct shape    <name> <block> [params] [-h [thickness]]
/mct gradient <name> <colors> [params] [-h [thickness]] [flags]
/mct tree     <woodtype> [options]
/mct path     <subcommand> [args]
/mct schematic <action> [args]
/mct undo / redo / cancel / pause / resume
/mct info / reload / wand / center / performance / build
```

> **Backward compatibility**: Short-form commands (`/mct sph`, `/mct cir`, `/mct gsph`, etc.) still work. They are hidden from tab completion but fully functional.

---

## Shape Commands

### Syntax

```
/mct shape <name> <block> [parameters...] [-h [thickness]]
```

- Add `-h` at the end to make any hollow-capable shape hollow (default thickness: 1)
- Add `-h <number>` to specify an explicit wall thickness

### 2D Shapes

| Name | Parameters | Hollow |
|---|---|---|
| `circle` | `<radius>` | `-h [thick]` |
| `square` | `<size>` | `-h [thick]` |
| `rectangle` | `<radiusX> <radiusZ> [cornerRadius]` | `-h [thick]` |
| `ellipse` | `<radiusX> <radiusZ>` | `-h [thick]` |
| `polygon` | `<radius> <sides>` (3–12) | `-h [thick]` |
| `star` | `<radius> <thickness>` | — |
| `line` | `<length> <thickness>` | — |
| `spiral` | `<radius> <turns> <thickness>` | — |

### 3D Shapes

| Name | Parameters | Hollow |
|---|---|---|
| `sphere` | `<radius>` | `-h [thick]` |
| `dome` | `<radius>` | `-h [thick]` |
| `cylinder` | `<height> <radius>` | `-h [thick]` |
| `cone` | `<height> <radius>` | `-h [thick]` |
| `pyramid` | `<height> <radius>` | `-h [thick]` |
| `arch` | `<legHeight> <radius> <width>` | `-h [thick]` |
| `torus` | `<majorRadius> <minorRadius>` | `-h [thick]` |
| `wall` | `<width> <height> <thickness>` | — |
| `helix` | `<height> <radius> <turns> <thickness>` | — |
| `tube` | `<radius> <height> <innerRadius>` | — |
| `capsule` | `<radius> <height>` | `-h [thick]` |
| `ellipsoid` | `<radiusX> <radiusY> <radiusZ>` | `-h [thick]` |
| `sectioncylinder` | `<radius> <sections> <sectionBlock>` | — |
| `tree` | `<woodtype> [options]` | — |

### Examples

```bash
# Basic filled shapes
/mct shape circle stone 15
/mct shape sphere glass 20
/mct shape cylinder oak_planks 10 8
/mct shape pyramid quartz_block 12 10
/mct shape torus stone_bricks 15 4

# Hollow shapes — -h alone = thickness 1
/mct shape circle stone 15 -h
/mct shape sphere glass 20 -h
/mct shape dome stone_bricks 18 -h

# Hollow shapes — explicit thickness
/mct shape sphere glass 20 -h 2
/mct shape cylinder oak_planks 10 8 -h 3
/mct shape ellipsoid quartz_block 12 8 10 -h 2
/mct shape torus stone_bricks 15 4 -h 1
/mct shape arch sandstone 6 8 5 -h 2
/mct shape pyramid smooth_stone 12 10 -h 3

# 2D hollow
/mct shape rectangle stone_bricks 10 6 -h 2
/mct shape ellipse oak_planks 12 8 -h 1
/mct shape polygon deepslate_bricks 10 6 -h 2

# Shapes with multiple params
/mct shape helix oak_log 20 5 3 2        - height=20, radius=5, turns=3, thickness=2
/mct shape tube stone_bricks 8 12 5      - radius=8, height=12, innerRadius=5
/mct shape spiral white_concrete 10 4 2  - radius=10, turns=4, thickness=2
/mct shape wall oak_planks 12 8 2        - width=12, height=8, thickness=2

# Rectangle with corner rounding
/mct shape rectangle stone 10 6          - sharp corners
/mct shape rectangle stone 10 6 2        - cornerRadius=2
/mct shape rectangle stone 10 6 2 -h 1  - hollow, cornerRadius=2, thickness=1

# Section Cylinder (dual-block)
/mct shape sectioncylinder stone 12 6 oak_planks   - 6 pie sections, outline=stone, fill=oak
/mct shape sectioncylinder iron_block 15 4 gold_block

# Polygon sides
/mct shape polygon stone 10 3            - triangle
/mct shape polygon stone 10 6            - hexagon
/mct shape polygon stone 10 6 -h 2      - hollow hexagon, thickness=2
/mct shape polygon stone 10 12          - 12-sided shape
```

---

## Gradient Commands

Apply smooth color gradients to any shape using hex color stops.

### Syntax

```
/mct gradient <name> <#hex1,#hex2,...> [parameters...] [-h [thickness]] [-dir <direction>] [-interp <mode>] [-unique]
```

- **Colors**: 2–6 comma-separated hex values, e.g. `#ff0000,#0000ff`
- **`-h [thick]`**: Hollow variant (same as shape command)
- **`-dir`**: Gradient direction — `y` (default), `x`, `z`, `radial`
- **`-interp`**: Color interpolation — `oklab` (default), `lab`, `rgb`, `hsl`
- **`-unique`**: Use only unique blocks (no repeated materials in the gradient)

> `tree` and `sectioncylinder` do not support gradients.

### Examples

```bash
# Basic gradient shapes
/mct gradient sphere #ff0000,#0000ff 20
/mct gradient circle #ffffff,#000000 15
/mct gradient cylinder #ffd700,#8b0000 10 8

# With gradient direction
/mct gradient sphere #ff0000,#0000ff 20 -dir radial
/mct gradient cylinder #ffd700,#8b0000 10 8 -dir y
/mct gradient ellipsoid #00ffff,#ff00ff 12 8 10 -dir x

# With interpolation mode
/mct gradient sphere #ff0000,#00ff00,#0000ff 15 -dir radial -interp oklab
/mct gradient dome #ff6b6b,#4ecdc4 12 -interp hsl
/mct gradient wall #ffffff,#aaaaaa,#555555,#000000 16 10 -interp lab

# Multi-stop gradients (up to 6 colors)
/mct gradient sphere #ff0000,#ff7700,#ffff00,#00ff00,#0000ff,#8b00ff 18
/mct gradient cylinder #ff6b6b,#ffd93d,#6bcb77,#4d96ff 12 10 -dir y

# Hollow gradient shapes
/mct gradient sphere #ff0000,#0000ff 20 -h
/mct gradient sphere #ff0000,#0000ff 20 -h 2
/mct gradient cylinder #ffd700,#8b0000 10 8 -h 3 -dir y
/mct gradient dome #00ffff,#ff00ff 14 -h 2 -interp oklab
/mct gradient torus #ff0000,#ffff00 12 3 -h 1

# Unique blocks only (no repeated materials)
/mct gradient sphere #ff0000,#0000ff 20 -unique
/mct gradient cylinder #ffd700,#8b0000 10 8 -dir y -unique

# Combining all flags
/mct gradient sphere #ff0000,#00ff00,#0000ff 15 -h 2 -dir radial -interp oklab -unique
```

---

## Tree Generator

Generate procedural trees with configurable parameters.

### Syntax

```
/mct tree <woodtype> [options]
```

**Wood types:** `oak` `spruce` `birch` `jungle` `acacia` `dark_oak` `mangrove` `cherry` `crimson` `warped`

**Options:**

| Option | Description | Default |
|---|---|---|
| `seed:<n>` | Random seed for reproducible trees | current time |
| `th:<n>` | Trunk height (4 – max) | `12` |
| `tr:<n>` | Trunk radius (1–6) | `2` |
| `bd:<n>` | Branch density (0.1–1.0) | `0.7` |
| `fd:<n>` | Foliage density (0.1–1.0) | `0.8` |
| `fr:<n>` | Foliage radius (2–15) | `6` |
| `-roots` | Generate surface roots | off |
| `-special` | Use special/rare blocks | off |

### Examples

```bash
/mct tree oak                                      - Default oak tree
/mct tree spruce th:20 tr:3                       - Tall spruce, thick trunk
/mct tree jungle th:30 fr:10 fd:0.9 -roots       - Giant jungle with roots
/mct tree cherry seed:12345                        - Reproducible cherry tree
/mct tree dark_oak th:16 bd:0.9 -roots -special  - Dense dark oak, special blocks
/mct tree birch th:14 tr:1 fr:4 fd:0.6           - Slim birch
```

---

## Path Tools

Shovel-based multipoint selection for generating roads, bridges, and smooth curve previews along a polyline.

### Workflow

1. **Get the tool** — `//wand` → pick **[Path Tool]**, or `/mct path tool enable` while holding a shovel
2. **Set a mode** — `/mct path mode road` (or `bridge` / `curve`)
3. **Define a path** — Left-click: set Pos1 (resets path). Right-click: append Pos2, Pos3, …
4. **Adjust settings** — `/mct path set width 7`, `/mct path set material smooth_stone`, etc.
5. **Preview** — `/mct path preview on` — particles trace the sampled curve
6. **Generate** — `/mct path generate`
7. **Undo** — `/mct undo`

### Selection System

| Action | Behavior |
|---|---|
| **Left-click** with shovel | Sets **Pos1**, resets all previous points |
| **Right-click** with shovel | Appends next point (Pos2, Pos3, …) |
| Minimum | **2 points** required |
| Ordering | Insertion order — path connects points sequentially |
| Allowed shovels | Configurable in `config.yml` (default: `WOODEN_SHOVEL`) |

### Commands

```
/mct path help                      - Show path tools help
/mct path tool enable|disable       - Enable/disable the shovel selection tool
/mct path mode road|bridge|curve    - Set the active generator mode
/mct path pos list                  - List selected positions with coordinates
/mct path pos undo                  - Remove the last added position
/mct path pos clear                 - Clear all positions
/mct path set                       - Open settings GUI for the active mode
/mct path set <key> <value>         - Change a setting via command
/mct path preview on|off            - Toggle particle preview of the sampled path
/mct path generate                  - Generate the structure along the path
/mct path particles on|off          - Toggle selection point/segment particles
/mct path clear                     - Reset selection (alias for pos clear)
/mct cancel                         - Cancel an in-progress generation
/mct undo                           - Undo the last generated structure
```

### Mode-Specific Settings

All settings are changed with `/mct path set <key> <value>`. Run `/mct path set` with no arguments to open the settings GUI.

**Curve Mode** (preview-only — no blocks placed):

| Key | Type | Default | Description |
|---|---|---|---|
| `resolution` | decimal 0.1–2.0 | `0.5` | Sampling density (lower = smoother) |
| `algorithm` | `catmullrom` / `bezier` | `catmullrom` | Interpolation algorithm |

**Road Mode:**

| Key | Type | Default | Description |
|---|---|---|---|
| `width` | integer 1–32 | `5` | Road width in blocks |
| `material` | block name | `STONE_BRICKS` | Main road surface |
| `border` | block name / `none` | `POLISHED_ANDESITE` | Edge material |
| `centerline` | block name / `none` | `none` | Center stripe |
| `use-slabs` | true/false | `true` | Slabs for gentle slopes |
| `use-stairs` | true/false | `true` | Stairs for steeper slopes |
| `terrain-adapt` | true/false | `true` | Follow terrain height |
| `clearance` | integer 1–10 | `3` | Air blocks above the road |
| `fill-below` | integer 0–20 | `4` | Depth of fill below road |
| `fill-material` | block name | `COBBLESTONE` | Sub-road fill material |
| `resolution` | decimal 0.1–2.0 | `0.5` | Curve sampling density |

**Bridge Mode:**

| Key | Type | Default | Description |
|---|---|---|---|
| `width` | integer 1–32 | `5` | Deck width in blocks |
| `deck-material` | block name | `STONE_BRICK_SLAB` | Deck surface |
| `railings` | true/false | `true` | Generate side railings |
| `railing-material` | block name | `STONE_BRICK_WALL` | Railing block |
| `supports` | true/false | `true` | Generate support pillars |
| `support-material` | block name | `STONE_BRICKS` | Pillar block |
| `support-spacing` | integer 3–50 | `8` | Distance between pillars |
| `support-width` | integer 1–10 | `3` | Pillar radius |
| `support-max-depth` | integer 1–128 | `40` | Max pillar depth |
| `height-mode` | `auto` / `fixed` | `auto` | Deck height behavior |
| `ramps` | true/false | `true` | Entry/exit ramps |
| `ramp-material` | block name | `STONE_BRICK_STAIRS` | Ramp stair block |
| `resolution` | decimal 0.1–2.0 | `0.5` | Curve sampling density |

### Settings GUI

Running `/mct path set` with no arguments opens an interactive chest GUI:

| GUI | Size | Accent |
|---|---|---|
| **Road Settings** | 54 slots | Orange |
| **Bridge Settings** | 54 slots | Light Blue |
| **Curve Settings** | 27 slots | Magenta |

- **Left-click** → increment / cycle forward / toggle ON
- **Right-click** → decrement / cycle backward / toggle OFF / set `none`
- **Shift-click** → larger numeric step

### Path Examples

```bash
# Stone road, 7 blocks wide
/mct path tool enable
/mct path mode road
# Left-click A, right-click B C D with shovel
/mct path set width 7
/mct path set material smooth_stone
/mct path set border polished_andesite
/mct path preview on
/mct path generate

# Wooden bridge over a valley
/mct path mode bridge
/mct path set deck-material oak_slab
/mct path set railings true
/mct path set railing-material oak_fence
/mct path set supports true
/mct path set support-material spruce_log
/mct path set support-spacing 6
/mct path preview on
/mct path generate

# Curve preview (no block placement)
/mct path mode curve
/mct path set resolution 0.3
/mct path set algorithm catmullrom
/mct path preview on
```

### Error Conditions

| Condition | Message |
|---|---|
| No mode selected | `Select a mode first: /mct path mode road\|bridge\|curve` |
| Fewer than 2 points | `Not enough points. Select at least 2 positions with the shovel.` |
| Points in different worlds | `All positions must be in the same world.` |
| Generate in curve mode | `Curve mode is preview-only. Switch to road or bridge to generate blocks.` |
| Path too long | `Path too long (X blocks). Maximum allowed: Y.` |
| Too many points | `Maximum points reached (50).` |
| Too many blocks | `Operation would place X blocks (max: Y)` |
| Generation in progress | `A generation is already in progress. Use /mct cancel first.` |

### Particles & Visual Feedback

- **Selection particles** (`HAPPY_VILLAGER`): at each point and along segments between points
- **Preview particles** (`FLAME`): along the sampled curve when preview is on; bridge pillar positions use `SOUL_FIRE_FLAME`
- Visible only to the selecting player
- Refresh: 10 ticks (selection), 20 ticks (preview)
- Max render distance: 128 blocks
- Auto-downsampled if the path exceeds 5,000 points

---

## Terrain Brush

Use a **bamboo** item as the brush tool. Left-click opens the GUI, right-click applies the brush.

```
/mcbrush                        - Open brush GUI
/mcbrush toggle|on|off          - Toggle brush on/off
/mcbrush size <n>               - Set brush radius
/mcbrush intensity <1-100>      - Set brush intensity
/mcbrush height <n>             - Set max height delta
/mcbrush block <block>          - Set block type
/mcbrush heightmap <name>       - Set heightmap
/mcbrush mode <mode>            - Set mode: raise, lower, smooth, flatten
/mcbrush autosmooth <on|off>    - Toggle auto-smooth after each stroke
/mcbrush smoothstrength <1-5>   - Set smooth strength
/mcbrush list                   - List available heightmaps
/mcbrush reload                 - Reload heightmaps from disk
/mcbrush info                   - Show current settings
/mcbrush help                   - Show help
```

**GUI features:**
- Interactive heightmap selector with ASCII preview
- Block selector with paginated display
- Configurable auto-rotation and circular mask
- Real-time preview mode

---

## Schematic System

```
/mct schematic list                 - List schematics with dimensions
/mct schematic load <name>          - Load a schematic (auto-unloads previous)
/mct schematic info                 - Show info about the loaded schematic
/mct schematic rotate <degrees>     - Rotate loaded schematic (90 / 180 / 270)
/mct schematic unload               - Unload current schematic
/mct schematic remove <name>        - Delete a schematic file
/mct paste [-a] [-p]                - Paste with 10s preview (-a = skip air, -p = skip preview)
```

Place `.schem` files in `plugins/MCTools/schematics/`.

---

## Admin & Utility Commands

```
/mct undo [count]       - Undo operations (default 1, max 100 per call, up to 1000 total)
/mct redo [count]       - Redo undone operations
/mct cancel             - Cancel current operation and restore preview blocks
/mct pause              - Pause current block placement
/mct resume             - Resume paused placement
/mct performance        - Show server performance (TPS, MSPT, RAM, historical averages)
/mct center             - Find the center of a nearby structure (200-block scan)
/mct wand               - Get the MCTools Terrain Brush wand
/mct reload             - Reload configuration (requires mctools.admin)
/mct info               - Plugin version and info
/mct help [shape]       - Show help, optionally for a specific shape
/mct debug update <ver> - Send a test update notification to yourself (requires mctools.admin)
```

### WorldEdit Integration

```
//undo [count]   - Also undoes MCTools operations
//redo [count]   - Also redoes MCTools operations
//wand           - Choose: WorldEdit axe, MCTools brush, or Path Tool shovel
```

---

## Tab Completion

MCTools provides smart tab completion at every level:

| What you type | What is suggested |
|---|---|
| `/mct ` | `shape` `gradient` `path` `tree` + all other commands |
| `/mct shape ` | All shape names: `circle` `sphere` `cylinder` … |
| `/mct shape circle ` | Block suggestions (inventory first, then common blocks) |
| `/mct shape circle stone ` | Radius suggestions: `5` `10` `15` … |
| `/mct shape circle stone 10 ` | `-h` flag |
| `/mct shape circle stone 10 -h ` | Thickness: `1` `2` `3` `4` `5` |
| `/mct gradient ` | Gradient-capable shape names |
| `/mct gradient sphere ` | Color presets: `#ff0000,#0000ff` … |
| `/mct gradient sphere #ff0000,#0000ff ` | Radius suggestions |
| `/mct gradient sphere #ff0000,#0000ff 20 ` | Flags: `-dir` `-interp` `-unique` `-h` |
| `/mct gradient … -dir ` | `y` `x` `z` `radial` |
| `/mct gradient … -interp ` | `oklab` `lab` `rgb` `hsl` |
| `/mct path ` | `help` `tool` `mode` `pos` `set` `preview` `generate` `particles` `clear` |
| `/mct path tool ` | `enable` `disable` |
| `/mct path mode ` | `road` `bridge` `curve` |
| `/mct path pos ` | `list` `undo` `clear` |
| `/mct path set ` | Setting keys for the active mode |
| `/mct path set <key> ` | Valid values for that key |
| `/mct path preview ` | `on` `off` |
| `/mct schematic ` | `list` `load` `unload` `remove` `info` `rotate` |
| `/mct schematic load ` | Available `.schem` file names |
| `/mct debug ` | `update` |
| `/mct debug update ` | `1.0.0` `1.1.0` `2.0.0` `99.9.9` |

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `mctools.use` | Basic usage | `true` |
| `mctools.admin` | Admin commands (reload, schematics, debug) | `op` |
| `mctools.bypass.limit` | Bypass block limits | `op` |
| `mctools.shapes.*` | All shape permissions | `op` |
| `mctools.gradient` | Gradient shape commands | `true` |
| `mctools.brush` | Terrain brush | `op` |
| `mctools.center` | `/mct center` | `op` |
| `mctools.path.use` | Path selection, mode, settings, preview | `op` |
| `mctools.path.generate` | Generate road/bridge structures | `op` |
| `mctools.path.bypass.limit` | Bypass max-points and max-path-length | `op` |
| `mctools.update.notify` | Receive update notifications on join | `op` |
| `mctools.*` | All permissions | `op` |

---

## Configuration

Edit `plugins/MCTools/config.yml` to customize:

- Maximum radius / height / thickness per operation
- Maximum blocks per operation (`max-blocks`)
- Cooldown between commands
- Async placement settings (batch size, delay)
- Preview duration and preview block type
- Sound and particle effects
- Brush defaults (size, intensity, max height, block)
- Path tool: allowed shovel materials, max points, max path length
- Performance monitor thresholds (TPS/RAM)
- Boss bar display settings

---

## Installation

1. Download the latest release from [GitHub Releases](https://github.com/PenguinStudiosOrganization/MCTools/releases/)
2. Place the JAR in your `plugins/` folder
3. (Optional) Install [WorldEdit](https://dev.bukkit.org/projects/worldedit) for schematic support and `//undo`
4. Restart the server
5. Place `.schem` files in `plugins/MCTools/schematics/` for schematic support
6. Edit `plugins/MCTools/config.yml` as needed and run `/mct reload`

---

## Requirements

- Minecraft 1.20+
- Paper or Spigot server
- Java 21+
- WorldEdit (optional — for schematics and `//undo` integration)

---

## License

MIT License — see LICENSE file for details.
