# MCT Path Tools — Technical Specification

> **Status:** Proposed feature set for MCTools v1.4.0  
> **Scope:** Curve Tool, Road Generator, Bridge Generator, Multipoint Selection System  
> **Authors:** PenguinStudios Development  
> **Constraint:** All player-facing messages **must** use the existing `MessageUtil` methods (`sendError`, `sendInfo`, `sendSuccess`, `sendWarning`, `sendRaw`, `sendUsage`, `sendHelpCommand`, etc.) and the standard `PREFIX` (`MCTools │`). No new message styles, prefixes, or color schemes may be introduced.

---

## Table of Contents

1. [Overview & User Workflow](#overview--user-workflow)  
2. [Selection System](#selection-system)  
3. [Modes](#modes)  
4. [Complete `/mct` Command Reference](#complete-mct-command-reference)  
5. [Mode-Specific Settings (`/mct set`)](#mode-specific-settings-mct-set)  
6. [Particles & Visual Feedback](#particles--visual-feedback)  
7. [Generator Details](#generator-details)  
   - [Curve](#curve-generator)  
   - [Road](#road-generator)  
   - [Bridge](#bridge-generator)  
8. [Settings List with Defaults](#settings-list-with-defaults)  
9. [Permissions](#permissions)  
10. [Error Messages & Behavior Rules](#error-messages--behavior-rules)  
11. [Performance & Safety Constraints](#performance--safety-constraints)  

---

## Overview & User Workflow

MCT Path Tools adds three path-based generators — **Curve**, **Road**, and **Bridge** — that share a unified multipoint selection system. Players define an ordered polyline path in-world using a **shovel** (WorldEdit-like left/right click), choose a mode, configure settings, preview the result with particles, and generate the structure. Everything is controlled via `/mct` subcommands; there are no GUI menus.

### Typical workflow

```
1.  /mct tool enable              ← Activate the shovel selection tool
2.  Left-click with shovel        ← Set Pos1 (resets any previous path)
3.  Right-click with shovel       ← Append Pos2, Pos3, Pos4, …
4.  /mct mode road                ← Select the Road mode
5.  /mct set width 5              ← (Optional) Adjust mode-specific settings
6.  /mct preview on               ← Show particle preview of the final path
7.  /mct generate                 ← Build the road along the path
8.  /mct undo                     ← Revert if needed
```

Steps 2–3 can be repeated at any time. Steps 5–6 are optional. The player may switch modes freely; settings are stored per-mode and persist until the player disconnects or clears them.

---

## Selection System

### Tool item

| Property | Value |
|---|---|
| Default item | `WOODEN_SHOVEL` |
| Configurable | Yes — `path-tool.shovel-materials` accepts a list of shovel material names (e.g. `WOODEN_SHOVEL`, `STONE_SHOVEL`, `IRON_SHOVEL`, `GOLDEN_SHOVEL`, `DIAMOND_SHOVEL`, `NETHERITE_SHOVEL`). Any shovel in the list is recognized. |
| Activation | The tool must be **enabled** per-player via `/mct tool enable` before clicks are intercepted. When disabled, shovel clicks behave normally. |

### Click behavior

| Action | Behavior |
|---|---|
| **Left click** (block) with enabled shovel | Sets **Pos1** at the clicked block's location. **Clears all previously stored points** (full path reset). Sends: `Pos1 set at (X, Y, Z). Path reset.` |
| **Right click** (block) with enabled shovel | Appends a new point (**Pos2**, **Pos3**, …) at the clicked block's location. Sends: `Pos<N> added at (X, Y, Z). (<N> points total)` |
| Left/Right click on **air** | Ignored (no action, no message). |

### Constraints

- **Minimum valid selection:** 2 positions. Any command that requires a valid path (`preview`, `generate`) must reject with an error if fewer than 2 points exist.
- **Maximum points:** Configurable via `path-tool.max-points` (default: `50`). Attempting to add beyond the limit sends an error.
- **Maximum total path length:** Configurable via `path-tool.max-path-length` (default: `2000` blocks measured as the sum of segment distances). Checked at `generate` time.
- Points are stored **per-player, per-session** (cleared on disconnect).
- The selection is an **ordered polyline** connecting points in insertion order: Pos1 → Pos2 → Pos3 → …

### Position management commands

See the [command reference](#complete-mct-command-reference) for `/mct pos list`, `/mct pos undo`, `/mct pos clear`.

---

## Modes

Three modes are available: **road**, **bridge**, **curve**.

- A player may have **at most one active mode** at a time.
- Selecting a new mode replaces the previous one. Mode-specific settings for the previous mode are **retained** (not cleared) so the player can switch back without reconfiguring.
- **If no mode is selected**, the following commands must return an error and do nothing:
  - `/mct preview on|off`
  - `/mct generate`
  - `/mct set <key> <value>`

The error message for this case is defined in [Error Messages](#error-messages--behavior-rules).

---

## Complete `/mct` Command Reference

All commands below are subcommands of the existing `/mct` (alias `/mctools`) root command. They extend the current command set — existing commands (`/mct cir`, `/mct sph`, `/mct undo`, etc.) remain unchanged.

### Help

```
/mct help path
```

Displays the path-tools help page listing all path-related subcommands. Uses the existing `sendHelpHeader` + `sendHelpCommand` formatting from `MessageUtil`.

**Permission:** `mctools.path.use`

---

### Tool enable / disable

```
/mct tool enable
/mct tool disable
```

Enables or disables the shovel selection tool for the executing player. When disabled, shovel clicks are not intercepted.

| Argument | Required | Description |
|---|---|---|
| `enable` / `disable` | Yes | Toggle state |

**Examples:**
```
/mct tool enable    → "Path tool enabled. Use a shovel to select points."
/mct tool disable   → "Path tool disabled."
```

**Permission:** `mctools.path.use`

---

### Mode selection

```
/mct mode <road|bridge|curve>
```

Sets the active generator mode for the player.

| Argument | Required | Description |
|---|---|---|
| `road` / `bridge` / `curve` | Yes | The mode to activate |

**Examples:**
```
/mct mode road      → "Mode set to Road."
/mct mode bridge    → "Mode set to Bridge."
/mct mode curve     → "Mode set to Curve."
```

**Permission:** `mctools.path.use`

---

### Position management

```
/mct pos list
/mct pos undo
/mct pos clear
```

| Subcommand | Behavior |
|---|---|
| `list` | Lists all currently stored positions with index, coordinates, and world. If no positions exist, sends an info message. |
| `undo` | Removes the **last** added position. If no positions remain, sends an error. |
| `clear` | Clears **all** stored positions. Confirms with a message. |

**Examples:**
```
/mct pos list
  → Pos1: (100, 64, -200)
    Pos2: (120, 65, -180)
    Pos3: (140, 63, -160)
    3 points total.

/mct pos undo       → "Removed Pos3 at (140, 63, -160). (2 points remaining)"
/mct pos clear      → "All positions cleared."
```

**Permission:** `mctools.path.use`

---

### Mode-specific settings

```
/mct set <key> <value>
```

Adjusts a setting for the **currently active mode**. If no mode is selected, returns the "no mode" error. Keys that do not apply to the current mode return an error listing valid keys.

Running `/mct set` with no arguments displays all current settings for the active mode.

See [Mode-Specific Settings](#mode-specific-settings-mct-set) for the full key/value table per mode.

**Examples:**
```
/mct set width 7            → "Road width set to 7."
/mct set material STONE     → "Road material set to STONE."
/mct set railings true      → "Bridge railings enabled."
/mct set resolution 0.25    → "Curve resolution set to 0.25."
```

**Permission:** `mctools.path.use`

---

### Preview

```
/mct preview on
/mct preview off
```

Toggles the **particle preview** of the generated structure along the current path using the current mode and settings.

| State | Behavior |
|---|---|
| `on` | Computes the sampled path and renders particles showing the footprint. Particles refresh on a timer and are visible only to the selecting player (unless `path-tool.preview-visible-to-all` is `true`). |
| `off` | Stops rendering preview particles. |

**Preconditions (all must be met or an error is returned):**
1. A mode must be selected.
2. At least 2 positions must exist.

**Examples:**
```
/mct preview on     → "Preview enabled for Road mode. Use /mct preview off to hide."
/mct preview off    → "Preview disabled."
```

**Permission:** `mctools.path.use`

---

### Generate

```
/mct generate
```

Generates the structure for the active mode along the current path. Uses the existing `BlockPlacer` pipeline (preview → batched async placement → undo registration → boss bar → completion message).

**Preconditions (all must be met or an error is returned):**
1. A mode must be selected.
2. At least 2 positions must exist.
3. Total path length must not exceed `path-tool.max-path-length`.
4. Estimated block count must not exceed `max-blocks`.
5. No other active generation task for this player.

On success, the operation is registered in both the MCTools `UndoManager` and (if available) WorldEdit `EditSession` history, exactly like existing shape commands.

**Examples:**
```
/mct generate       → "Generating 12,450 blocks..."
                      (boss bar, progress, completion message — identical to existing shapes)
```

**Permission:** `mctools.path.generate`

---

### Cancel

```
/mct cancel
```

Cancels the current generation or preview operation. This is the **existing** `/mct cancel` command — no change in behavior. It already handles both `PlacementTask` and `PreviewTask` cancellation.

**Permission:** `mctools.use`

---

### Undo

```
/mct undo [count]
```

This is the **existing** `/mct undo` command. Because path-tool generations are registered in `UndoManager` (and optionally WorldEdit history), undo works identically to shape undo — no special handling required.

**Permission:** `mctools.use`

---

### Particles toggle (selection markers)

```
/mct particles on|off
```

Toggles the **selection point/segment particles** (the markers shown at each selected position and the connecting lines between them). This is independent of the preview particles.

| State | Behavior |
|---|---|
| `on` (default) | Show small particles at each stored position and thin lines between consecutive points. |
| `off` | Hide selection markers. |

**Examples:**
```
/mct particles on   → "Selection particles enabled."
/mct particles off  → "Selection particles disabled."
```

**Permission:** `mctools.path.use`

---

## Mode-Specific Settings (`/mct set`)

All settings have sensible defaults. Players only need to change what they want to customize.

### Curve settings

| Key | Type | Default | Description |
|---|---|---|---|
| `resolution` | Decimal (0.1–2.0) | `0.5` | Distance in blocks between sampled points on the interpolated curve. Lower = smoother, more points. |
| `algorithm` | `catmullrom` / `bezier` | `catmullrom` | Interpolation algorithm. Catmull-Rom passes through all control points; Bézier uses them as control handles. |

### Road settings

| Key | Type | Default | Description |
|---|---|---|---|
| `width` | Integer (1–32) | `5` | Total road width in blocks (including borders). |
| `material` | Material name | `STONE_BRICKS` | Main road surface block. |
| `border` | Material name / `none` | `POLISHED_ANDESITE` | Border block placed on both edges. Set to `none` to disable. |
| `centerline` | Material name / `none` | `none` | Center-line block (single block width down the middle). Set to `none` to disable. |
| `use-slabs` | `true` / `false` | `true` | Use slabs for half-block elevation changes (smoother slopes). |
| `use-stairs` | `true` / `false` | `true` | Use stairs for directional slope transitions. |
| `terrain-adapt` | `true` / `false` | `true` | Adapt road surface to follow terrain elevation (fill gaps below, clear blocks above within a configurable clearance). |
| `clearance` | Integer (1–10) | `3` | Number of air blocks to ensure above the road surface when `terrain-adapt` is enabled. |
| `fill-below` | Integer (0–20) | `4` | Maximum depth of support blocks placed below the road to connect it to terrain. 0 = no fill. |
| `fill-material` | Material name | `COBBLESTONE` | Block used for sub-surface fill. |
| `resolution` | Decimal (0.1–2.0) | `0.5` | Curve sampling resolution (same as Curve mode). |

### Bridge settings

| Key | Type | Default | Description |
|---|---|---|---|
| `width` | Integer (1–32) | `5` | Bridge deck width in blocks. |
| `deck-material` | Material name | `STONE_BRICK_SLAB` | Block used for the bridge deck surface. |
| `railings` | `true` / `false` | `true` | Place railing walls on both edges of the deck. |
| `railing-material` | Material name | `STONE_BRICK_WALL` | Block used for railings. |
| `supports` | `true` / `false` | `true` | Generate vertical support pillars from the deck down to terrain. |
| `support-material` | Material name | `STONE_BRICKS` | Block used for support pillars. |
| `support-spacing` | Integer (3–50) | `8` | Distance in blocks between support pillars along the path. |
| `support-max-depth` | Integer (1–128) | `40` | Maximum downward distance a support pillar will extend searching for solid ground. If no ground is found, the pillar stops at max depth. |
| `height-mode` | `fixed` / `auto` | `auto` | `fixed`: deck is placed at the Y-level of each control point. `auto`: deck maintains a smooth interpolated height, ignoring terrain dips. |
| `ramps` | `true` / `false` | `true` | Generate slab/stair ramps at the start and end of the bridge to connect to ground level. |
| `ramp-material` | Material name | `STONE_BRICK_STAIRS` | Stair block used for ramps. |
| `resolution` | Decimal (0.1–2.0) | `0.5` | Curve sampling resolution. |

---

## Particles & Visual Feedback

### Selection particles (point & segment markers)

| Property | Value |
|---|---|
| Particle type | `HAPPY_VILLAGER` (configurable via `path-tool.selection-particle`) |
| Point marker | 4 particles in a small cross pattern at each stored position, refreshed every `path-tool.particle-interval` ticks (default: `10`). |
| Segment lines | Particles spaced 0.5 blocks apart along straight lines between consecutive points. |
| Visibility | Shown **only to the selecting player** by default. Configurable via `path-tool.preview-visible-to-all` (default: `false`). |
| Distance culling | Particles are not sent if the player is more than `path-tool.particle-max-distance` blocks away (default: `128`). |
| Toggle | `/mct particles on|off` |

### Preview particles (generated footprint)

| Property | Value |
|---|---|
| Particle type | `FLAME` for road/bridge deck outline, `SOUL_FIRE_FLAME` for supports/pillars (configurable via `path-tool.preview-particle` and `path-tool.preview-support-particle`). |
| Rendering | After `/mct preview on`, the full sampled path is computed and particles are rendered along the footprint edges and fill area at the configured `resolution`. |
| Refresh interval | Every `path-tool.preview-interval` ticks (default: `20` — once per second). |
| Auto-disable | Preview automatically turns off when `/mct generate` is executed or positions are cleared. |
| Visibility | Same rules as selection particles (player-only by default). |

### Performance constraints

- Particle tasks run on the **server main thread** using `BukkitRunnable` scheduled repeating tasks.
- If the sampled path exceeds `path-tool.max-preview-points` (default: `5000`), the preview is downsampled to stay within budget and a warning is sent: `"Preview downsampled for performance (5000 point limit)."`
- Particle tasks are **cancelled** when the player disconnects, disables the tool, or clears positions.

---

## Generator Details

### Curve Generator

The Curve generator does **not** place blocks. It computes and visualizes a smooth interpolated curve through the selected control points. Its primary purpose is to let players design and preview a path before committing to Road or Bridge generation. However, it can also be used standalone to visualize curves.

**Algorithm:**
- **Catmull-Rom** (default): The curve passes through every control point. Tangents are computed from neighboring points. Endpoint tangents are extrapolated.
- **Bézier**: Control points are used as Bézier handles. The curve passes through the first and last point; intermediate points pull the curve toward them.

**Sampling:** The curve is evaluated at intervals of `resolution` blocks (default `0.5`), producing a list of `(x, y, z)` sample points. This sampled point list is what Road and Bridge generators consume.

**`/mct generate` in Curve mode:** Sends an info message: `"Curve mode is preview-only. Switch to road or bridge to generate blocks."` — no blocks are placed.

---

### Road Generator

Generates a solid road surface along the sampled curve path.

**Generation steps:**
1. **Sample the path** using the Curve engine at the configured `resolution`.
2. **For each sample point**, compute the path tangent direction (forward vector).
3. **Expand laterally** by `width / 2` blocks perpendicular to the tangent on both sides.
4. **Place surface blocks** (`material`) across the width. Place `border` blocks on the outermost columns. Place `centerline` block on the center column (if width is odd and centerline ≠ `none`).
5. **Terrain adaptation** (if `terrain-adapt` is `true`):
   - For each column, scan downward up to `fill-below` blocks. If there is air/liquid below the road surface, fill with `fill-material`.
   - Scan upward up to `clearance` blocks above the surface. Replace non-air blocks with air.
6. **Slope handling** (if `use-slabs` / `use-stairs` is `true`):
   - When the elevation change between consecutive sample points is exactly 0.5 blocks (half step), place a slab instead of a full block.
   - When the elevation change is 1 full block, place a stair oriented in the direction of travel.
7. **Liquid handling:** Road surface blocks replace water/lava. Sub-surface fill also replaces liquids.
8. **Air behavior:** Road is placed regardless of what exists at the target location (replaces everything).

---

### Bridge Generator

Generates a bridge deck with optional railings, supports, and ramps along the sampled curve path.

**Generation steps:**
1. **Sample the path** using the Curve engine at the configured `resolution`.
2. **Determine deck height** per sample point:
   - `fixed` mode: Use the Y coordinate of the nearest control point (interpolated linearly between control points).
   - `auto` mode: Use the smooth curve Y value from the interpolation algorithm, producing a gentle arc.
3. **Place deck blocks** (`deck-material`) across the `width` at the computed height.
4. **Railings** (if `railings` is `true`): Place `railing-material` blocks one block above the deck on both edges.
5. **Supports** (if `supports` is `true`):
   - Every `support-spacing` blocks along the path, generate a vertical pillar of `support-material` from the deck downward.
   - The pillar extends until it hits a solid (non-air, non-liquid) block or reaches `support-max-depth`.
   - Pillar width: 1 block (centered under the deck). For deck widths ≥ 7, two pillars are placed (one under each edge).
6. **Ramps** (if `ramps` is `true`):
   - At the first and last sample points, generate a descending stair/slab ramp from deck height down to the terrain surface.
   - Ramp uses `ramp-material` (stairs) and the deck material (slabs) for half-steps.
   - Ramp length is automatically calculated based on height difference (1 block horizontal per 1 block vertical).
7. **Liquid/air behavior:** Deck and supports replace liquids. Air below the deck (between supports) is left as-is.

---

## Settings List with Defaults

### `config.yml` additions (under a new `path-tool` section)

```yaml
# ============================================
# PATH TOOL SETTINGS (Curve / Road / Bridge)
# ============================================

path-tool:
  # Enable the path tool system
  enabled: true

  # Shovel types recognized as the selection tool
  shovel-materials:
    - WOODEN_SHOVEL

  # Maximum number of control points per selection
  max-points: 50

  # Maximum total path length in blocks (sum of segment distances)
  max-path-length: 2000

  # Selection particles
  selection-particle: HAPPY_VILLAGER
  particle-interval: 10          # ticks between particle refreshes
  particle-max-distance: 128     # blocks; don't send particles beyond this

  # Preview particles
  preview-particle: FLAME
  preview-support-particle: SOUL_FIRE_FLAME
  preview-interval: 20           # ticks between preview refreshes
  max-preview-points: 5000       # downsample if exceeded
  preview-visible-to-all: false  # true = all nearby players see particles

  # Default generator settings (per-mode defaults; players override with /mct set)
  curve:
    resolution: 0.5
    algorithm: catmullrom         # catmullrom | bezier

  road:
    width: 5
    material: STONE_BRICKS
    border: POLISHED_ANDESITE
    centerline: none
    use-slabs: true
    use-stairs: true
    terrain-adapt: true
    clearance: 3
    fill-below: 4
    fill-material: COBBLESTONE
    resolution: 0.5

  bridge:
    width: 5
    deck-material: STONE_BRICK_SLAB
    railings: true
    railing-material: STONE_BRICK_WALL
    supports: true
    support-material: STONE_BRICKS
    support-spacing: 8
    support-max-depth: 40
    height-mode: auto             # fixed | auto
    ramps: true
    ramp-material: STONE_BRICK_STAIRS
    resolution: 0.5
```

All values above serve as **server-wide defaults**. Players can override them per-session using `/mct set <key> <value>`. Player overrides are not persisted across disconnects.

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `mctools.path.*` | `op` | Grants all path-tool permissions (parent node). |
| `mctools.path.use` | `op` | Use the selection tool, set mode, manage positions, adjust settings, toggle particles, and preview. |
| `mctools.path.generate` | `op` | Execute `/mct generate` to place blocks. |
| `mctools.path.bypass.limit` | `op` | Bypass `max-path-length` and `max-points` limits. |

These integrate into the existing permission tree:

```yaml
mctools.*:
  children:
    mctools.path.*: true
    # ... existing children ...

mctools.path.*:
  default: op
  children:
    mctools.path.use: true
    mctools.path.generate: true
    mctools.path.bypass.limit: true
```

---

## Error Messages & Behavior Rules

All messages below are sent using the existing `MessageUtil` methods. The prefix `MCTools │` and color scheme (red `#ef4444` for errors via `sendError`, blue `#3b82f6` for info via `sendInfo`, green `#10b981` for success via `sendSuccess`, orange `#f97316` for warnings via `sendWarning`) are inherited automatically.

### "No mode selected" rule (non-negotiable)

Any command that depends on an active mode **must** check for a selected mode first. If none is set, the command returns immediately after sending:

> **✗** Select a mode first: `/mct mode road|bridge|curve`

Sent via `MessageUtil.sendError(player, "Select a mode first: /mct mode road|bridge|curve")`.

**Affected commands:** `/mct preview on`, `/mct generate`, `/mct set <key> <value>`.

### Complete error message table

| Condition | Message (via `sendError`) |
|---|---|
| No mode selected | `Select a mode first: /mct mode road|bridge|curve` |
| Fewer than 2 positions | `Not enough points. Select at least 2 positions with the shovel.` |
| Max points exceeded | `Maximum points reached (50). Remove a point with /mct pos undo or clear with /mct pos clear.` |
| Max path length exceeded | `Path too long (2,345 blocks). Maximum allowed: 2,000. Remove points or increase the limit.` |
| Max blocks exceeded | Delegated to existing `PerformanceMonitor.checkOperationSafety()` — uses its standard message. |
| Invalid setting key | `Unknown setting "<key>" for <Mode> mode. Valid keys: <comma-separated list>` |
| Invalid setting value | `Invalid value "<value>" for <key>. Expected: <type description>.` |
| Invalid material name | `Unknown block type "<value>". Use a valid Minecraft material name.` |
| Tool already enabled | `Path tool is already enabled.` (sent via `sendInfo`) |
| Tool already disabled | `Path tool is already disabled.` (sent via `sendInfo`) |
| No positions to undo | `No positions to remove.` |
| No positions to list | `No positions stored. Use a shovel to select points.` (sent via `sendInfo`) |
| Generate in Curve mode | `Curve mode is preview-only. Switch to road or bridge to generate blocks.` (sent via `sendInfo`) |
| Active task exists | `A generation is already in progress. Use /mct cancel first.` |
| Player in protected area | `Cannot generate here. This area is protected.` (integration: if WorldGuard or similar is detected, check build permission at sample points before generating. If not detectable, skip this check.) |

### Info/success messages

| Event | Message (method) |
|---|---|
| Pos1 set | `sendInfo`: `Pos1 set at (X, Y, Z). Path reset.` |
| PosN added | `sendInfo`: `Pos<N> added at (X, Y, Z). (<N> points total)` |
| Position removed | `sendInfo`: `Removed Pos<N> at (X, Y, Z). (<remaining> points remaining)` |
| Positions cleared | `sendInfo`: `All positions cleared.` |
| Mode set | `sendInfo`: `Mode set to <Mode>.` |
| Setting changed | `sendInfo`: `<Mode> <key> set to <value>.` |
| Tool enabled | `sendInfo`: `Path tool enabled. Use a shovel to select points.` |
| Tool disabled | `sendInfo`: `Path tool disabled.` |
| Preview enabled | `sendInfo`: `Preview enabled for <Mode> mode. Use /mct preview off to hide.` |
| Preview disabled | `sendInfo`: `Preview disabled.` |
| Preview downsampled | `sendWarning`: `Preview downsampled for performance (5,000 point limit).` |
| Generation started | Delegated to existing `BlockPlacer` pipeline (boss bar, progress, etc.) |
| Generation complete | `sendSuccess`: `Generated <Mode> with <N> blocks` (standard `sendSuccess` format) |
| Particles on | `sendInfo`: `Selection particles enabled.` |
| Particles off | `sendInfo`: `Selection particles disabled.` |
| Settings list | `sendRaw`: Formatted list of current mode settings with colored keys and values. |

---

## Performance & Safety Constraints

### Block placement

- Generation **must** use the existing `BlockPlacer` pipeline. This ensures:
  - Batched per-tick placement (adaptive `blocks-per-tick` via `PerformanceMonitor`).
  - Boss bar with ETA.
  - Progress messages.
  - Undo registration (both MCTools `UndoManager` and WorldEdit `EditSession`).
  - Pause/resume support (`/mct pause`, `/mct resume`).
  - Emergency cancellation on TPS drop.
- The path-tool generators produce a `Map<Location, BlockData>` and hand it to `BlockPlacer.placeGradientBlocks()` — no custom placement loop.

### Path computation

- Curve sampling and block map computation should run **synchronously on the main thread** (the data set is bounded by `max-path-length` × `width` which is at most ~64,000 blocks for a 2000-length, 32-wide road — well within a single tick budget).
- If the estimated block count exceeds `max-blocks`, abort before computing the full block map.

### Particle tasks

- Selection particle task: 1 `BukkitRunnable` per player, scheduled at `particle-interval` ticks. Cancelled on disconnect / tool disable / pos clear.
- Preview particle task: 1 `BukkitRunnable` per player, scheduled at `preview-interval` ticks. Cancelled on disconnect / preview off / generate / pos clear.
- Maximum 2 particle tasks per player at any time.

### Edge cases

| Case | Behavior |
|---|---|
| Fewer than 2 points → generate | Error message, no action. |
| Exactly 2 points | Valid. Produces a straight-line path (no curve interpolation needed). |
| 3+ points | Full curve interpolation applied. |
| All points at same Y | Flat path — no slopes, no ramps. |
| Points in different worlds | Error: `All positions must be in the same world.` |
| Very steep path (>45°) | Road: slabs/stairs used where possible; vertical sections use full blocks stacked. Bridge: deck follows the curve; supports extend downward as normal. |
| Path crosses liquids | Road: replaces liquid with road/fill blocks. Bridge: supports extend through liquid to solid ground. |
| Path crosses existing builds | Blocks are replaced. The undo system allows reversal. No automatic protection check beyond WorldGuard integration (if present). |
| Player disconnects mid-generation | Existing `BlockPlacer` behavior: task is cancelled, partial placement remains, undo history is available on reconnect (if within `max-undo-history`). |
| `/mct generate` while preview active | Preview is automatically cancelled (existing `cancelPreview` behavior), then generation starts. |

---

## README Section (for insertion into the main README.md)

The following is the user-facing documentation block to be added to the main README under a new `### Path Tools (Curve / Road / Bridge)` section within **What's New** and the **Commands** section.

---

### 🛤️ Path Tools (Curve / Road / Bridge)

Build roads, bridges, and smooth curves along a multi-point path. Select points with a shovel, choose a mode, and generate.

**Quick start:**
```
/mct tool enable                    - Enable the shovel selection tool
  Left-click with shovel            - Set Pos1 (resets path)
  Right-click with shovel           - Add Pos2, Pos3, …
/mct mode road                      - Select Road mode
/mct preview on                     - Preview with particles
/mct generate                       - Build the road
```

**Path Tool Commands:**
```
/mct tool enable|disable            - Enable/disable the shovel selection tool
/mct mode road|bridge|curve         - Set the active generator mode
/mct pos list                       - List all selected positions
/mct pos undo                       - Remove the last position
/mct pos clear                      - Clear all positions
/mct set <key> <value>              - Change a mode-specific setting
/mct set                            - Show all settings for the current mode
/mct preview on|off                 - Toggle particle preview
/mct generate                       - Generate the structure along the path
/mct particles on|off               - Toggle selection point/segment particles
/mct help path                      - Show path tools help
```

**Road settings** (`/mct set <key> <value>` when mode = road):
```
width <1-32>              (default: 5)        - Road width
material <block>          (default: STONE_BRICKS) - Surface block
border <block|none>       (default: POLISHED_ANDESITE) - Edge block
centerline <block|none>   (default: none)     - Center line block
use-slabs <true|false>    (default: true)     - Slabs for half-steps
use-stairs <true|false>   (default: true)     - Stairs for slopes
terrain-adapt <true|false>(default: true)     - Adapt to terrain
clearance <1-10>          (default: 3)        - Air blocks above road
fill-below <0-20>         (default: 4)        - Support depth below road
fill-material <block>     (default: COBBLESTONE) - Sub-surface fill block
resolution <0.1-2.0>      (default: 0.5)      - Curve sampling resolution
```

**Bridge settings** (`/mct set <key> <value>` when mode = bridge):
```
width <1-32>              (default: 5)        - Deck width
deck-material <block>     (default: STONE_BRICK_SLAB) - Deck block
railings <true|false>     (default: true)     - Edge railings
railing-material <block>  (default: STONE_BRICK_WALL) - Railing block
supports <true|false>     (default: true)     - Vertical support pillars
support-material <block>  (default: STONE_BRICKS) - Pillar block
support-spacing <3-50>    (default: 8)        - Distance between pillars
support-max-depth <1-128> (default: 40)       - Max pillar depth
height-mode <fixed|auto>  (default: auto)     - Deck height calculation
ramps <true|false>        (default: true)     - Entry/exit ramps
ramp-material <block>     (default: STONE_BRICK_STAIRS) - Ramp stair block
resolution <0.1-2.0>      (default: 0.5)      - Curve sampling resolution
```

**Curve settings** (`/mct set <key> <value>` when mode = curve):
```
resolution <0.1-2.0>      (default: 0.5)      - Sampling resolution
algorithm <catmullrom|bezier> (default: catmullrom) - Interpolation method
```

**Path Tool Permissions:**

| Permission | Description |
|---|---|
| `mctools.path.*` | All path tool permissions |
| `mctools.path.use` | Selection tool, modes, settings, preview |
| `mctools.path.generate` | Generate structures |
| `mctools.path.bypass.limit` | Bypass point/length limits |
