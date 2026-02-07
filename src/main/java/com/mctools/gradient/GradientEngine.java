package com.mctools.gradient;

import com.mctools.gradient.ColorMath.ColorSpaceConverter;

import java.util.*;

/**
 * Core gradient generation engine.
 * Interpolates colors in perceptual color space and matches to Minecraft blocks.
 */
public class GradientEngine {

    public record GradientBlock(String blockId, String blockName, String hexColor, int r, int g, int b) {}

    // ─── Block quality scoring ──────────────────────────────────────
    // Color accuracy is the primary factor.
    // Only glazed terracotta is penalized (complex multi-color patterns
    // whose average color doesn't represent the block well).
    // Concrete/wool get a tiny bonus as tiebreaker when colors are equal.

    private static int getBlockQualityScore(BlockColor.Entry block) {
        String id = block.id();

        // Glazed terracotta - complex patterns, average color misleading
        if (id.contains("glazed")) return 10;

        // Premium tiebreaker - solid uniform colors
        if ((id.contains("_concrete") || id.endsWith("concrete"))
                && !id.contains("powder")) {
            return 0;
        }
        if (id.contains("_wool") || id.endsWith("wool")) return 0;

        // Everything else - all blocks are valid, tiny tiebreaker only
        return 1;
    }

    // ─── Enhanced color scoring ─────────────────────────────────────

    private static double getEnhancedColorScore(double[] targetLab, double[] blockLab,
                                                  BlockColor.Entry block, double[] prevLab,
                                                  boolean isGrayscale) {
        double baseDistance = ColorMath.deltaE(targetLab, blockLab);
        int qualityPenalty = getBlockQualityScore(block);
        double score = baseDistance;

        int[] blockRgb = {block.r(), block.g(), block.b()};
        double blockSaturation = ColorMath.getColorSaturation(blockRgb[0], blockRgb[1], blockRgb[2]);

        if (isGrayscale) {
            double blockChroma = ColorMath.getChroma(blockLab);

            if (!ColorMath.isGrayscaleColor(blockRgb[0], blockRgb[1], blockRgb[2])) {
                score += 500;
            }
            if (blockSaturation > 0.1) {
                score += blockSaturation * 1000;
            }
            if (blockChroma > 0.015) {
                score += blockChroma * 800;
            }

            double lightnessError = Math.abs(targetLab[0] - blockLab[0]);
            score += lightnessError * 15;

            // Penalize pink/magenta
            boolean isPinkish = blockRgb[0] > blockRgb[1]
                && blockRgb[0] > blockRgb[2] * 0.8
                && blockRgb[1] < blockRgb[0] * 0.9;
            if (isPinkish && blockSaturation > 0.05) {
                score += 300;
            }
        } else {
            double targetChroma = ColorMath.getChroma(targetLab);

            if (targetChroma < 0.03) {
                if (blockSaturation > 0.15) score += blockSaturation * 300;
                if (!ColorMath.isGrayscaleColor(blockRgb[0], blockRgb[1], blockRgb[2])) {
                    score += 100;
                }
            }

            double lightnessError = Math.abs(targetLab[0] - blockLab[0]);
            score += lightnessError * 3;
        }

        // Quality penalties (minimal - color accuracy dominates)
        if (qualityPenalty >= 10) score += 8;        // glazed terracotta
        else if (qualityPenalty >= 1) score += 0.5;  // tiebreaker for non-premium

        // Hue continuity
        if (prevLab != null && !isGrayscale) {
            double targetHue = ColorMath.getHueAngle(targetLab);
            double blockHue = ColorMath.getHueAngle(blockLab);
            double prevHue = ColorMath.getHueAngle(prevLab);

            double hueDiff = ColorMath.hueDifference(targetHue, blockHue);
            score += hueDiff * 0.3;

            double expectedDir = targetHue - prevHue;
            double actualDir = blockHue - prevHue;
            if (Math.abs(expectedDir) > 10 && Math.abs(actualDir) > 10) {
                if ((expectedDir > 0 && actualDir < -20) || (expectedDir < 0 && actualDir > 20)) {
                    score += 50;
                }
            }

            // Penalize cyan in purple gradients
            boolean isCyanBlock = blockRgb[1] > blockRgb[0] && blockRgb[2] > blockRgb[0]
                && Math.abs(blockRgb[1] - blockRgb[2]) < 50;
            boolean isPurpleTarget = targetLab[1] > 0 && targetLab[2] < 0;
            if (isCyanBlock && isPurpleTarget) score += 150;

            // Chroma jumps
            double prevChroma = ColorMath.getChroma(prevLab);
            double tgtChroma = ColorMath.getChroma(targetLab);
            double blkChroma = ColorMath.getChroma(blockLab);
            double expectedChromaChange = tgtChroma - prevChroma;
            double actualChromaChange = blkChroma - prevChroma;
            if (Math.signum(expectedChromaChange) != Math.signum(actualChromaChange)
                    && Math.abs(actualChromaChange) > 0.02) {
                score += Math.abs(actualChromaChange - expectedChromaChange) * 20;
            }
        }

        return score;
    }

    // ─── Interpolation ──────────────────────────────────────────────

    private static List<double[]> interpolateMultiColor(List<String> hexColors, int numSteps,
                                                         ColorSpaceConverter converter) {
        if (hexColors.size() < 2 || numSteps < 2) return Collections.emptyList();

        List<double[]> targets = new ArrayList<>(numSteps);
        int segments = hexColors.size() - 1;

        for (int i = 0; i < numSteps; i++) {
            double t = (double) i / (numSteps - 1);
            int segIdx = Math.min((int) Math.floor(t * segments), segments - 1);
            double localT = t * segments - segIdx;

            int[] rgb1 = ColorMath.hexToRgb(hexColors.get(segIdx));
            int[] rgb2 = ColorMath.hexToRgb(hexColors.get(segIdx + 1));
            double[] lab1 = converter.convert(rgb1[0], rgb1[1], rgb1[2]);
            double[] lab2 = converter.convert(rgb2[0], rgb2[1], rgb2[2]);

            targets.add(ColorMath.mixInColorSpace(lab1, lab2, localT));
        }
        return targets;
    }

    // ─── Block matching - Default mode (with penalties) ─────────────

    private static List<MatchedBlock> assignWithPenalties(List<CachedBlock> blocks,
                                                           List<double[]> targets,
                                                           boolean isGrayscale) {
        List<MatchedBlock> result = new ArrayList<>(targets.size());

        for (int i = 0; i < targets.size(); i++) {
            CachedBlock bestBlock = null;
            double bestScore = Double.MAX_VALUE;

            double[] prevLab = i > 0 ? result.get(i - 1).lab : null;

            for (CachedBlock cb : blocks) {
                double score = getEnhancedColorScore(targets.get(i), cb.lab, cb.entry, prevLab, isGrayscale);

                // Repetition penalties
                if (i > 0 && result.get(i - 1).entry.name().equals(cb.entry.name())) score += 100;
                if (i > 1 && result.get(i - 2).entry.name().equals(cb.entry.name())) score += 50;
                if (i > 2 && result.get(i - 3).entry.name().equals(cb.entry.name())) score += 25;

                // Hue direction penalty
                if (prevLab != null) {
                    double prevHue = ColorMath.getHueAngle(prevLab);
                    double targetHue = ColorMath.getHueAngle(targets.get(i));
                    double blockHue = ColorMath.getHueAngle(cb.lab);
                    double expectedDir = targetHue - prevHue;
                    double actualDir = blockHue - prevHue;
                    if (Math.abs(expectedDir) > 10 && Math.abs(actualDir) > 10) {
                        if ((expectedDir > 0 && actualDir < -30) || (expectedDir < 0 && actualDir > 30)) {
                            score += 40;
                        }
                    }
                }

                if (score < bestScore) {
                    bestScore = score;
                    bestBlock = cb;
                }
            }

            result.add(new MatchedBlock(bestBlock.entry, bestBlock.lab,
                ColorMath.deltaE(targets.get(i), bestBlock.lab)));
        }
        return result;
    }

    // ─── Block matching - Unique mode ───────────────────────────────

    private static List<MatchedBlock> assignUniqueBlocks(List<CachedBlock> blocks,
                                                          List<double[]> targets,
                                                          boolean isGrayscale) {
        int numSteps = targets.size();

        // Compute cost matrix
        double[][] costs = new double[numSteps][blocks.size()];
        for (int i = 0; i < numSteps; i++) {
            for (int j = 0; j < blocks.size(); j++) {
                double cost = ColorMath.deltaE(targets.get(i), blocks.get(j).lab);
                if (isGrayscale) {
                    BlockColor.Entry b = blocks.get(j).entry;
                    double sat = ColorMath.getColorSaturation(b.r(), b.g(), b.b());
                    if (!ColorMath.isGrayscaleColor(b.r(), b.g(), b.b())) cost += 500;
                    if (sat > 0.1) cost += sat * 1000;
                }
                costs[i][j] = cost;
            }
        }

        // Build candidate lists
        List<List<int[]>> candidates = new ArrayList<>();
        for (int i = 0; i < numSteps; i++) {
            List<int[]> scored = new ArrayList<>();
            for (int j = 0; j < blocks.size(); j++) {
                scored.add(new int[]{j, (int)(costs[i][j] * 1000)});
            }
            scored.sort(Comparator.comparingInt(a -> a[1]));
            int limit = Math.min(numSteps * 2, blocks.size());
            candidates.add(scored.subList(0, limit));
        }

        // Greedy assignment with hue continuity
        Set<Integer> used = new HashSet<>();
        List<MatchedBlock> result = new ArrayList<>();

        for (int i = 0; i < numSteps; i++) {
            CachedBlock bestBlock = null;
            double bestScore = Double.MAX_VALUE;
            int bestIdx = -1;
            double[] prevLab = i > 0 ? result.get(i - 1).lab : null;

            for (int[] cand : candidates.get(i)) {
                int j = cand[0];
                if (used.contains(j)) continue;

                double score = deltaEWithHuePenalty(targets.get(i), blocks.get(j).lab, prevLab, 0.4);

                if (prevLab != null) {
                    double prevHue = ColorMath.getHueAngle(prevLab);
                    double targetHue = ColorMath.getHueAngle(targets.get(i));
                    double blockHue = ColorMath.getHueAngle(blocks.get(j).lab);
                    double expectedDir = targetHue - prevHue;
                    double actualDir = blockHue - prevHue;
                    if (Math.abs(expectedDir) > 10 && Math.abs(actualDir) > 10) {
                        if ((expectedDir > 0 && actualDir < -30) || (expectedDir < 0 && actualDir > 30)) {
                            score += 50;
                        }
                    }
                }

                // Future-cost lookahead
                for (int k = i + 1; k < numSteps; k++) {
                    double futureCost = costs[k][j];
                    double bestAlt = Double.MAX_VALUE;
                    for (int[] altCand : candidates.get(k)) {
                        if (!used.contains(altCand[0]) && altCand[0] != j) {
                            bestAlt = Math.min(bestAlt, costs[k][altCand[0]]);
                            break;
                        }
                    }
                    if (bestAlt < Double.MAX_VALUE && futureCost < bestAlt * 0.7 && futureCost < score * 0.8) {
                        score += (bestAlt - futureCost) * 0.5 * (1.0 - (double)(k - i) / numSteps);
                    }
                }

                if (score < bestScore) {
                    bestScore = score;
                    bestBlock = blocks.get(j);
                    bestIdx = j;
                }
            }

            // Fallback: pick any unused block
            if (bestBlock == null) {
                for (int j = 0; j < blocks.size(); j++) {
                    if (used.contains(j)) continue;
                    double score = prevLab != null
                        ? deltaEWithHuePenalty(targets.get(i), blocks.get(j).lab, prevLab, 0.4)
                        : costs[i][j];
                    if (score < bestScore) {
                        bestScore = score;
                        bestBlock = blocks.get(j);
                        bestIdx = j;
                    }
                }
            }

            used.add(bestIdx);
            result.add(new MatchedBlock(bestBlock.entry, bestBlock.lab, costs[i][bestIdx]));
        }

        // Swap optimization
        boolean improved = true;
        int iterations = 0;
        while (improved && iterations < 10) {
            improved = false;
            iterations++;
            for (int i = 0; i < result.size() - 1; i++) {
                double currentCost = ColorMath.deltaE(targets.get(i), result.get(i).lab)
                    + ColorMath.deltaE(targets.get(i + 1), result.get(i + 1).lab);
                double swappedCost = ColorMath.deltaE(targets.get(i), result.get(i + 1).lab)
                    + ColorMath.deltaE(targets.get(i + 1), result.get(i).lab);
                if (swappedCost < currentCost - 0.5) {
                    MatchedBlock tmp = result.get(i);
                    result.set(i, result.get(i + 1));
                    result.set(i + 1, tmp);
                    improved = true;
                }
            }
        }

        return result;
    }

    private static double deltaEWithHuePenalty(double[] lab1, double[] lab2, double[] prevLab, double weight) {
        double baseDelta = ColorMath.deltaE(lab1, lab2);
        if (prevLab == null) return baseDelta;

        double hue1 = ColorMath.getHueAngle(lab1);
        double hue2 = ColorMath.getHueAngle(lab2);
        double hueDiff = ColorMath.hueDifference(hue1, hue2);

        // Cyan jump penalty
        boolean isCyanJump = prevLab[2] < 0 && lab1[2] < 0 && lab2[2] > 0.02;
        double cyanPenalty = isCyanJump ? hueDiff * 0.5 : 0;

        return baseDelta + (hueDiff * weight * 0.1) + cyanPenalty;
    }

    // ─── Public API ─────────────────────────────────────────────────

    public List<GradientBlock> generateGradient(List<String> hexColors, int numSteps,
                                                 String interpolationMode, boolean uniqueOnly) {
        List<BlockColor.Entry> entries = BlockColor.getFilteredBlocks();
        if (entries.isEmpty() || hexColors.size() < 2) return Collections.emptyList();

        ColorSpaceConverter converter = ColorMath.getConverter(interpolationMode);

        // Interpolate target colors
        List<double[]> targets = interpolateMultiColor(hexColors, numSteps, converter);
        if (targets.isEmpty()) return Collections.emptyList();

        // Convert block database to same color space (using perceived colors)
        List<CachedBlock> cachedBlocks = new ArrayList<>(entries.size());
        for (BlockColor.Entry e : entries) {
            int[] perceived = BlockColor.getPerceivedRgb(e);
            double[] lab = converter.convert(perceived[0], perceived[1], perceived[2]);
            cachedBlocks.add(new CachedBlock(e, lab));
        }

        // Detect grayscale gradient
        boolean isGrayscale = isGrayscaleGradient(hexColors);

        // Match blocks
        List<MatchedBlock> matched = uniqueOnly
            ? assignUniqueBlocks(cachedBlocks, targets, isGrayscale)
            : assignWithPenalties(cachedBlocks, targets, isGrayscale);

        // Convert to output format
        List<GradientBlock> result = new ArrayList<>(matched.size());
        for (MatchedBlock m : matched) {
            BlockColor.Entry e = m.entry;
            result.add(new GradientBlock(e.id(), e.name(), e.hex(), e.r(), e.g(), e.b()));
        }
        return result;
    }

    private static boolean isGrayscaleGradient(List<String> hexColors) {
        for (String hex : hexColors) {
            int[] rgb = ColorMath.hexToRgb(hex);
            if (!ColorMath.isGrayscaleColor(rgb[0], rgb[1], rgb[2])) return false;
        }
        return true;
    }

    // ─── Internal types ─────────────────────────────────────────────

    private record CachedBlock(BlockColor.Entry entry, double[] lab) {}
    private record MatchedBlock(BlockColor.Entry entry, double[] lab, double distance) {}
}
