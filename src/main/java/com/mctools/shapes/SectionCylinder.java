package com.mctools.shapes;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.*;

/**
 * Section Cylinder — generates a flat disc divided into equal angular sections.
 *
 * Command: /mct scyl <block> <radius> <sections> <sectionBlock>
 *
 * How it works, step by step:
 *
 * 1. The shape iterates over every (x, z) position inside a circle of the given radius.
 *    A block is considered inside the circle when x² + z² <= (radius + 0.5)².
 *
 * 2. For each block inside the circle, we classify it into one of three categories:
 *
 *    a) OUTLINE — the outer ring of the circle.
 *       A block belongs to the outline when its distance from center >= (radius - 0.8).
 *
 *    b) CENTER HUB — a small filled circle at the very center (distance <= 1.5 blocks).
 *       This gives the "wheel hub" look where all divider lines converge.
 *
 *    c) DIVIDER LINE — a radial line from center to edge separating two sections.
 *       The circle is split into N equal angular slices (360° / N each).
 *       For each section boundary at angle θ = s × (2π / N), we compute the
 *       perpendicular distance from the block to the line through the origin
 *       with direction (cos θ, sin θ):
 *
 *           perpDist = |x × sin(θ) − z × cos(θ)|
 *
 *       If perpDist is within the line thickness AND the dot-product projection
 *       (x × cos(θ) + z × sin(θ)) is non-negative (so we only draw the ray,
 *       not the opposite direction), the block is marked as a divider.
 *
 *       Line thickness is adaptive: max(0.8, min(1.5, radius × 0.08)), so small
 *       circles get thin lines and large circles get slightly thicker ones.
 *
 *    d) FILL — everything else. These blocks form the interior of each pie slice.
 *
 * 3. Outline, center hub, and divider blocks are placed with the primary block.
 *    Fill blocks are placed with the section block.
 *    The result is a Map<Location, BlockData> passed to BlockPlacer.placeGradientBlocks().
 */
public class SectionCylinder extends Shape3D {

    private final int radius;
    private final int sections;
    private BlockData sectionBlockData;

    public SectionCylinder(int radius, int sections) {
        super(false, 1);
        this.radius = radius;
        this.sections = Math.max(2, sections);
    }

    public void setSectionBlockData(BlockData sectionBlockData) {
        this.sectionBlockData = sectionBlockData;
    }

    public BlockData getSectionBlockData() {
        return sectionBlockData;
    }

    public Map<Location, BlockData> generateWithSections(Location center, BlockData primaryBlock) {
        Map<Location, BlockData> blockMap = new LinkedHashMap<>();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        double radiusSq = (radius + 0.5) * (radius + 0.5);
        double angleStep = 2.0 * Math.PI / sections;
        double lineThickness = Math.max(0.8, Math.min(1.5, radius * 0.08));

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distSq = x * x + z * z;
                if (distSq > radiusSq) continue;

                double dist = Math.sqrt(distSq);
                boolean isOutline = dist >= (radius - 0.8);
                boolean isDivider = false;

                if (!isOutline && dist > 1.2) {
                    for (int s = 0; s < sections; s++) {
                        double lineAngle = s * angleStep;
                        double lx = Math.cos(lineAngle);
                        double lz = Math.sin(lineAngle);
                        double perpDist = Math.abs(x * lz - z * lx);
                        double projection = x * lx + z * lz;

                        if (perpDist <= lineThickness && projection >= -0.5) {
                            isDivider = true;
                            break;
                        }
                    }
                }

                boolean isCenter = dist <= 1.5;
                Location loc = new Location(world, cx + x, cy, cz + z);

                if (isOutline || isDivider || isCenter) {
                    blockMap.put(loc, primaryBlock);
                } else if (sectionBlockData != null) {
                    blockMap.put(loc, sectionBlockData);
                } else {
                    blockMap.put(loc, primaryBlock);
                }
            }
        }

        return blockMap;
    }

    @Override
    public List<Location> generate(Location center) {
        List<Location> blocks = createBlockList();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        double radiusSq = (radius + 0.5) * (radius + 0.5);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distSq = x * x + z * z;
                if (distSq <= radiusSq) {
                    addBlock(blocks, world, cx, cy, cz, x, 0, z);
                }
            }
        }

        return blocks;
    }

    @Override
    public String getName() {
        return "Section Cylinder";
    }

    @Override
    public String getPermission() {
        return "mctools.shapes.sectioncylinder";
    }

    @Override
    public String getUsage() {
        return "/mct scyl <block> <radius> <sections> <sectionBlock>";
    }

    @Override
    public String getDescription() {
        return "Creates a flat disc divided into equal angular sections with divider lines";
    }

    @Override
    public int getEstimatedBlockCount() {
        return (int) (Math.PI * radius * radius);
    }
}
