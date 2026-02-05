package com.mctools.gradient;

import java.util.*;

/**
 * Hardcoded database of Minecraft block colors.
 * Colors represent the average RGB of each block's texture.
 * Ported from HueBlocks/blocks.js - all 505 blocks with exact hex values.
 */
public final class BlockColor {

    public record Entry(String id, String name, int r, int g, int b, String category) {
        public String hex() {
            return String.format("#%02x%02x%02x", r, g, b);
        }
    }

    private static final List<Entry> ALL_BLOCKS = new ArrayList<>();
    private static final Set<String> NON_SOLID_BLOCKS = new HashSet<>();
    private static final Map<String, int[]> DOMINANT_COLOR_OVERRIDES = new HashMap<>();
    private static List<Entry> filteredCache = null;

    static {
        initBlocks();
        initNonSolid();
        initDominantOverrides();
    }

    private static void add(String id, String name, String hex, String category) {
        int[] rgb = ColorMath.hexToRgb(hex);
        ALL_BLOCKS.add(new Entry(id, name, rgb[0], rgb[1], rgb[2], category));
    }

    private static void initBlocks() {
        // ==================== WOOL (16) ====================
        add("white_wool", "White Wool", "#e9ecec", "wool");
        add("orange_wool", "Orange Wool", "#f07613", "wool");
        add("magenta_wool", "Magenta Wool", "#bd44b3", "wool");
        add("light_blue_wool", "Light Blue Wool", "#3ab3da", "wool");
        add("yellow_wool", "Yellow Wool", "#f8c627", "wool");
        add("lime_wool", "Lime Wool", "#70b919", "wool");
        add("pink_wool", "Pink Wool", "#ed8dac", "wool");
        add("gray_wool", "Gray Wool", "#3e4447", "wool");
        add("light_gray_wool", "Light Gray Wool", "#8e8e86", "wool");
        add("cyan_wool", "Cyan Wool", "#158991", "wool");
        add("purple_wool", "Purple Wool", "#7b2fbe", "wool");
        add("blue_wool", "Blue Wool", "#35399d", "wool");
        add("brown_wool", "Brown Wool", "#724728", "wool");
        add("green_wool", "Green Wool", "#546d1b", "wool");
        add("red_wool", "Red Wool", "#a12722", "wool");
        add("black_wool", "Black Wool", "#141519", "wool");

        // ==================== CONCRETE (16) ====================
        add("white_concrete", "White Concrete", "#cfd5d6", "concrete");
        add("orange_concrete", "Orange Concrete", "#e06101", "concrete");
        add("magenta_concrete", "Magenta Concrete", "#a9309f", "concrete");
        add("light_blue_concrete", "Light Blue Concrete", "#23b4d9", "concrete");
        add("yellow_concrete", "Yellow Concrete", "#f0af15", "concrete");
        add("lime_concrete", "Lime Concrete", "#5ea818", "concrete");
        add("pink_concrete", "Pink Concrete", "#d5658e", "concrete");
        add("gray_concrete", "Gray Concrete", "#36393d", "concrete");
        add("light_gray_concrete", "Light Gray Concrete", "#7d7d73", "concrete");
        add("cyan_concrete", "Cyan Concrete", "#157788", "concrete");
        add("purple_concrete", "Purple Concrete", "#64209c", "concrete");
        add("blue_concrete", "Blue Concrete", "#2c2e8f", "concrete");
        add("brown_concrete", "Brown Concrete", "#60331a", "concrete");
        add("green_concrete", "Green Concrete", "#495b24", "concrete");
        add("red_concrete", "Red Concrete", "#8e2020", "concrete");
        add("black_concrete", "Black Concrete", "#080a0f", "concrete");

        // ==================== CONCRETE POWDER (16) ====================
        add("white_concrete_powder", "White Concrete Powder", "#e2e4e4", "concrete");
        add("orange_concrete_powder", "Orange Concrete Powder", "#e38420", "concrete");
        add("magenta_concrete_powder", "Magenta Concrete Powder", "#c053b0", "concrete");
        add("light_blue_concrete_powder", "Light Blue Concrete Powder", "#6bc5d9", "concrete");
        add("yellow_concrete_powder", "Yellow Concrete Powder", "#e9c739", "concrete");
        add("lime_concrete_powder", "Lime Concrete Powder", "#94bb3d", "concrete");
        add("pink_concrete_powder", "Pink Concrete Powder", "#e59ab5", "concrete");
        add("gray_concrete_powder", "Gray Concrete Powder", "#4d5154", "concrete");
        add("light_gray_concrete_powder", "Light Gray Concrete Powder", "#9a9a94", "concrete");
        add("cyan_concrete_powder", "Cyan Concrete Powder", "#24939d", "concrete");
        add("purple_concrete_powder", "Purple Concrete Powder", "#8236b1", "concrete");
        add("blue_concrete_powder", "Blue Concrete Powder", "#4649a6", "concrete");
        add("brown_concrete_powder", "Brown Concrete Powder", "#7e5536", "concrete");
        add("green_concrete_powder", "Green Concrete Powder", "#61772d", "concrete");
        add("red_concrete_powder", "Red Concrete Powder", "#a83632", "concrete");
        add("black_concrete_powder", "Black Concrete Powder", "#1a1b20", "concrete");

        // ==================== TERRACOTTA (17) ====================
        add("terracotta", "Terracotta", "#985e43", "terracotta");
        add("white_terracotta", "White Terracotta", "#d1b2a1", "terracotta");
        add("orange_terracotta", "Orange Terracotta", "#a15325", "terracotta");
        add("magenta_terracotta", "Magenta Terracotta", "#95586c", "terracotta");
        add("light_blue_terracotta", "Light Blue Terracotta", "#706c8a", "terracotta");
        add("yellow_terracotta", "Yellow Terracotta", "#ba8523", "terracotta");
        add("lime_terracotta", "Lime Terracotta", "#677534", "terracotta");
        add("pink_terracotta", "Pink Terracotta", "#a14e4e", "terracotta");
        add("gray_terracotta", "Gray Terracotta", "#392a24", "terracotta");
        add("light_gray_terracotta", "Light Gray Terracotta", "#876a61", "terracotta");
        add("cyan_terracotta", "Cyan Terracotta", "#565b5b", "terracotta");
        add("purple_terracotta", "Purple Terracotta", "#764556", "terracotta");
        add("blue_terracotta", "Blue Terracotta", "#4a3b5b", "terracotta");
        add("brown_terracotta", "Brown Terracotta", "#4d3323", "terracotta");
        add("green_terracotta", "Green Terracotta", "#4c532a", "terracotta");
        add("red_terracotta", "Red Terracotta", "#8f3d2e", "terracotta");
        add("black_terracotta", "Black Terracotta", "#251610", "terracotta");

        // ==================== GLAZED TERRACOTTA (16) ====================
        add("white_glazed_terracotta", "White Glazed Terracotta", "#bcd4cb", "terracotta");
        add("orange_glazed_terracotta", "Orange Glazed Terracotta", "#5bc3c1", "terracotta");
        add("magenta_glazed_terracotta", "Magenta Glazed Terracotta", "#d262a0", "terracotta");
        add("light_blue_glazed_terracotta", "Light Blue Glazed Terracotta", "#4eb4d0", "terracotta");
        add("yellow_glazed_terracotta", "Yellow Glazed Terracotta", "#eac058", "terracotta");
        add("lime_glazed_terracotta", "Lime Glazed Terracotta", "#a2c537", "terracotta");
        add("pink_glazed_terracotta", "Pink Glazed Terracotta", "#eb9eb3", "terracotta");
        add("gray_glazed_terracotta", "Gray Glazed Terracotta", "#5b6164", "terracotta");
        add("light_gray_glazed_terracotta", "Light Gray Glazed Terracotta", "#9eaeb0", "terracotta");
        add("cyan_glazed_terracotta", "Cyan Glazed Terracotta", "#4f8f90", "terracotta");
        add("purple_glazed_terracotta", "Purple Glazed Terracotta", "#6d3198", "terracotta");
        add("blue_glazed_terracotta", "Blue Glazed Terracotta", "#2f408a", "terracotta");
        add("brown_glazed_terracotta", "Brown Glazed Terracotta", "#776a55", "terracotta");
        add("green_glazed_terracotta", "Green Glazed Terracotta", "#758e38", "terracotta");
        add("red_glazed_terracotta", "Red Glazed Terracotta", "#b53b35", "terracotta");
        add("black_glazed_terracotta", "Black Glazed Terracotta", "#43302a", "terracotta");

        // ==================== GLASS (18) ====================
        add("glass", "Glass", "#c0d6dd", "glass");
        add("white_stained_glass", "White Stained Glass", "#ffffff", "glass");
        add("orange_stained_glass", "Orange Stained Glass", "#d87f33", "glass");
        add("magenta_stained_glass", "Magenta Stained Glass", "#b24cd8", "glass");
        add("light_blue_stained_glass", "Light Blue Stained Glass", "#6699d8", "glass");
        add("yellow_stained_glass", "Yellow Stained Glass", "#e5e533", "glass");
        add("lime_stained_glass", "Lime Stained Glass", "#7fcc19", "glass");
        add("pink_stained_glass", "Pink Stained Glass", "#f27fa5", "glass");
        add("gray_stained_glass", "Gray Stained Glass", "#4c4c4c", "glass");
        add("light_gray_stained_glass", "Light Gray Stained Glass", "#999999", "glass");
        add("cyan_stained_glass", "Cyan Stained Glass", "#4c7f99", "glass");
        add("purple_stained_glass", "Purple Stained Glass", "#7f3fb2", "glass");
        add("blue_stained_glass", "Blue Stained Glass", "#334cb2", "glass");
        add("brown_stained_glass", "Brown Stained Glass", "#664c33", "glass");
        add("green_stained_glass", "Green Stained Glass", "#667f33", "glass");
        add("red_stained_glass", "Red Stained Glass", "#993333", "glass");
        add("black_stained_glass", "Black Stained Glass", "#191919", "glass");
        add("tinted_glass", "Tinted Glass", "#2a2330", "glass");

        // ==================== WOOD PLANKS (12) ====================
        add("oak_planks", "Oak Planks", "#a68b4e", "wood");
        add("spruce_planks", "Spruce Planks", "#735431", "wood");
        add("birch_planks", "Birch Planks", "#c8b77a", "wood");
        add("jungle_planks", "Jungle Planks", "#9f7653", "wood");
        add("acacia_planks", "Acacia Planks", "#ad5d32", "wood");
        add("dark_oak_planks", "Dark Oak Planks", "#3e2912", "wood");
        add("mangrove_planks", "Mangrove Planks", "#773636", "wood");
        add("cherry_planks", "Cherry Planks", "#e0a8a0", "wood");
        add("bamboo_planks", "Bamboo Planks", "#c4a94d", "wood");
        add("crimson_planks", "Crimson Planks", "#6a344b", "wood");
        add("warped_planks", "Warped Planks", "#2b6963", "wood");
        add("pale_oak_planks", "Pale Oak Planks", "#d9cfc0", "wood");

        // ==================== WOOD LOGS (12) ====================
        add("oak_log", "Oak Wood", "#6b5839", "wood");
        add("spruce_log", "Spruce Wood", "#3b2912", "wood");
        add("birch_log", "Birch Wood", "#d5d5ce", "wood");
        add("jungle_log", "Jungle Wood", "#554a2e", "wood");
        add("acacia_log", "Acacia Wood", "#676157", "wood");
        add("dark_oak_log", "Dark Oak Wood", "#3b3019", "wood");
        add("mangrove_log", "Mangrove Wood", "#544f32", "wood");
        add("cherry_log", "Cherry Wood", "#331c24", "wood");
        add("bamboo_block", "Bamboo Block", "#7a9a2d", "wood");
        add("crimson_stem", "Crimson Stem", "#7b3953", "wood");
        add("warped_stem", "Warped Stem", "#3a5d5a", "wood");
        add("pale_oak_log", "Pale Oak Wood", "#c4baa8", "wood");

        // ==================== STRIPPED WOOD (12) ====================
        add("stripped_oak_log", "Stripped Oak Wood", "#b29157", "wood");
        add("stripped_spruce_log", "Stripped Spruce Wood", "#745a36", "wood");
        add("stripped_birch_log", "Stripped Birch Wood", "#c5b47b", "wood");
        add("stripped_jungle_log", "Stripped Jungle Wood", "#ab8a5e", "wood");
        add("stripped_acacia_log", "Stripped Acacia Wood", "#b05d3a", "wood");
        add("stripped_dark_oak_log", "Stripped Dark Oak Wood", "#4a3521", "wood");
        add("stripped_mangrove_log", "Stripped Mangrove Wood", "#7a3535", "wood");
        add("stripped_cherry_log", "Stripped Cherry Wood", "#dba9a0", "wood");
        add("stripped_bamboo_block", "Stripped Bamboo Block", "#c4a94d", "wood");
        add("stripped_crimson_stem", "Stripped Crimson Stem", "#893e5a", "wood");
        add("stripped_warped_stem", "Stripped Warped Stem", "#3a9992", "wood");
        add("stripped_pale_oak_log", "Stripped Pale Oak Wood", "#d9cfc0", "wood");

        // ==================== STONE VARIANTS ====================
        add("stone", "Stone", "#7d7d7d", "stone");
        add("cobblestone", "Cobblestone", "#7f7f7f", "stone");
        add("mossy_cobblestone", "Mossy Cobblestone", "#6a7a5a", "stone");
        add("stone_bricks", "Stone Bricks", "#7a7a7a", "stone");
        add("mossy_stone_bricks", "Mossy Stone Bricks", "#6d7a5d", "stone");
        add("cracked_stone_bricks", "Cracked Stone Bricks", "#767676", "stone");
        add("chiseled_stone_bricks", "Chiseled Stone Bricks", "#777777", "stone");
        add("smooth_stone", "Smooth Stone", "#9e9e9e", "stone");
        add("granite", "Granite", "#956755", "stone");
        add("polished_granite", "Polished Granite", "#946251", "stone");
        add("diorite", "Diorite", "#bfbfbf", "stone");
        add("polished_diorite", "Polished Diorite", "#c0c0c0", "stone");
        add("andesite", "Andesite", "#888888", "stone");
        add("polished_andesite", "Polished Andesite", "#848484", "stone");
        add("deepslate", "Deepslate", "#505050", "stone");
        add("cobbled_deepslate", "Cobbled Deepslate", "#4d4d4d", "stone");
        add("polished_deepslate", "Polished Deepslate", "#484848", "stone");
        add("deepslate_bricks", "Deepslate Bricks", "#464646", "stone");
        add("deepslate_tiles", "Deepslate Tiles", "#363636", "stone");
        add("chiseled_deepslate", "Chiseled Deepslate", "#363636", "stone");
        add("reinforced_deepslate", "Reinforced Deepslate", "#4a4a4a", "stone");
        add("tuff", "Tuff", "#6c6c5e", "stone");
        add("polished_tuff", "Polished Tuff", "#6c6c5e", "stone");
        add("tuff_bricks", "Tuff Bricks", "#6a6a5c", "stone");
        add("chiseled_tuff", "Chiseled Tuff", "#6c6c5e", "stone");
        add("chiseled_tuff_bricks", "Chiseled Tuff Bricks", "#6a6a5c", "stone");
        add("calcite", "Calcite", "#dfe0dc", "stone");
        add("dripstone_block", "Dripstone Block", "#866a5a", "stone");
        add("prismarine", "Prismarine", "#63a495", "stone");
        add("prismarine_bricks", "Prismarine Bricks", "#63ab9e", "stone");
        add("dark_prismarine", "Dark Prismarine", "#335b4b", "stone");
        add("sandstone", "Sandstone", "#d8cb9a", "stone");
        add("smooth_sandstone", "Smooth Sandstone", "#d8cb9a", "stone");
        add("cut_sandstone", "Cut Sandstone", "#d8cb9a", "stone");
        add("chiseled_sandstone", "Chiseled Sandstone", "#d8cb9a", "stone");
        add("red_sandstone", "Red Sandstone", "#a5521e", "stone");
        add("smooth_red_sandstone", "Smooth Red Sandstone", "#a5521e", "stone");
        add("cut_red_sandstone", "Cut Red Sandstone", "#a5521e", "stone");
        add("chiseled_red_sandstone", "Chiseled Red Sandstone", "#a5521e", "stone");
        add("bricks", "Bricks", "#966464", "stone");
        add("mud_bricks", "Mud Bricks", "#89674f", "stone");
        add("packed_mud", "Packed Mud", "#8e7057", "stone");
        add("quartz_block", "Quartz Block", "#ebe6de", "stone");
        add("smooth_quartz", "Smooth Quartz", "#ebe6de", "stone");
        add("quartz_bricks", "Quartz Bricks", "#ebe6de", "stone");
        add("chiseled_quartz_block", "Chiseled Quartz Block", "#e7e2da", "stone");
        add("quartz_pillar", "Quartz Pillar", "#ebe6de", "stone");

        // ==================== COPPER BLOCKS ====================
        add("copper_block", "Copper Block", "#c06b4e", "stone");
        add("exposed_copper", "Exposed Copper", "#a1826a", "stone");
        add("weathered_copper", "Weathered Copper", "#6d9466", "stone");
        add("oxidized_copper", "Oxidized Copper", "#52a384", "stone");
        add("cut_copper", "Cut Copper", "#bf6a4e", "stone");
        add("exposed_cut_copper", "Exposed Cut Copper", "#9f8168", "stone");
        add("weathered_cut_copper", "Weathered Cut Copper", "#6d9466", "stone");
        add("oxidized_cut_copper", "Oxidized Cut Copper", "#4f9e7f", "stone");
        add("waxed_copper_block", "Waxed Copper Block", "#c06b4e", "stone");
        add("copper_grate", "Copper Grate", "#c06b4e", "stone");
        add("exposed_copper_grate", "Exposed Copper Grate", "#a1826a", "stone");
        add("weathered_copper_grate", "Weathered Copper Grate", "#6d9466", "stone");
        add("oxidized_copper_grate", "Oxidized Copper Grate", "#52a384", "stone");
        add("chiseled_copper", "Chiseled Copper", "#c06b4e", "stone");
        add("exposed_chiseled_copper", "Exposed Chiseled Copper", "#a1826a", "stone");
        add("weathered_chiseled_copper", "Weathered Chiseled Copper", "#6d9466", "stone");
        add("oxidized_chiseled_copper", "Oxidized Chiseled Copper", "#52a384", "stone");
        add("copper_bulb", "Copper Bulb", "#c06b4e", "stone");

        // ==================== ORE BLOCKS ====================
        add("coal_block", "Coal Block", "#101010", "stone");
        add("iron_block", "Iron Block", "#d8d8d8", "stone");
        add("gold_block", "Gold Block", "#f6d03d", "stone");
        add("diamond_block", "Diamond Block", "#62ece5", "stone");
        add("emerald_block", "Emerald Block", "#2bbd5a", "stone");
        add("lapis_block", "Lapis Block", "#1e4a9b", "stone");
        add("redstone_block", "Redstone Block", "#a81e09", "stone");
        add("raw_iron_block", "Raw Iron Block", "#a6896b", "stone");
        add("raw_gold_block", "Raw Gold Block", "#dda92e", "stone");
        add("raw_copper_block", "Raw Copper Block", "#9a6b4e", "stone");
        add("amethyst_block", "Amethyst Block", "#8561b8", "stone");
        add("budding_amethyst", "Budding Amethyst", "#8561b8", "stone");
        add("netherite_block", "Netherite Block", "#423d3f", "stone");

        // ==================== OBSIDIAN ====================
        add("obsidian", "Obsidian", "#0f0a18", "stone");
        add("crying_obsidian", "Crying Obsidian", "#200a30", "stone");

        // ==================== CORAL BLOCKS (10) ====================
        add("tube_coral_block", "Tube Coral Block", "#3257ce", "natural");
        add("brain_coral_block", "Brain Coral Block", "#cf5a9a", "natural");
        add("bubble_coral_block", "Bubble Coral Block", "#a61aa1", "natural");
        add("fire_coral_block", "Fire Coral Block", "#a3232b", "natural");
        add("horn_coral_block", "Horn Coral Block", "#d9c63a", "natural");
        add("dead_tube_coral_block", "Dead Tube Coral Block", "#857e79", "natural");
        add("dead_brain_coral_block", "Dead Brain Coral Block", "#857e79", "natural");
        add("dead_bubble_coral_block", "Dead Bubble Coral Block", "#857e79", "natural");
        add("dead_fire_coral_block", "Dead Fire Coral Block", "#857e79", "natural");
        add("dead_horn_coral_block", "Dead Horn Coral Block", "#857e79", "natural");

        // ==================== NATURAL BLOCKS ====================
        add("grass_block", "Grass Block", "#7cbd6b", "natural");
        add("dirt", "Dirt", "#866043", "natural");
        add("coarse_dirt", "Coarse Dirt", "#77553b", "natural");
        add("rooted_dirt", "Rooted Dirt", "#90674a", "natural");
        add("dirt_path", "Dirt Path", "#94703a", "natural");
        add("podzol", "Podzol", "#6a4f35", "natural");
        add("mycelium", "Mycelium", "#6f6265", "natural");
        add("mud", "Mud", "#3c3837", "natural");
        add("muddy_mangrove_roots", "Muddy Mangrove Roots", "#443c35", "natural");
        add("clay", "Clay", "#a4a8b8", "natural");
        add("gravel", "Gravel", "#837f7e", "natural");
        add("sand", "Sand", "#dbd3a0", "natural");
        add("red_sand", "Red Sand", "#be6621", "natural");
        add("suspicious_sand", "Suspicious Sand", "#d8d0a0", "natural");
        add("suspicious_gravel", "Suspicious Gravel", "#837f7e", "natural");
        add("snow_block", "Snow Block", "#f9fffe", "natural");
        add("powder_snow", "Powder Snow", "#f5fcfc", "natural");
        add("ice", "Ice", "#91b4fe", "natural");
        add("packed_ice", "Packed Ice", "#8db4fc", "natural");
        add("blue_ice", "Blue Ice", "#74a7fd", "natural");
        add("moss_block", "Moss Block", "#596d2e", "natural");
        add("moss_carpet", "Moss Carpet", "#596d2e", "natural");
        add("azalea_leaves", "Azalea Leaves", "#5a7537", "natural");
        add("flowering_azalea_leaves", "Flowering Azalea Leaves", "#6a8547", "natural");
        add("oak_leaves", "Oak Leaves", "#4a7a32", "natural");
        add("spruce_leaves", "Spruce Leaves", "#3d5c2e", "natural");
        add("birch_leaves", "Birch Leaves", "#5a7a3a", "natural");
        add("jungle_leaves", "Jungle Leaves", "#3a8a2a", "natural");
        add("acacia_leaves", "Acacia Leaves", "#4a8a32", "natural");
        add("dark_oak_leaves", "Dark Oak Leaves", "#3a6a2a", "natural");
        add("mangrove_leaves", "Mangrove Leaves", "#4a7a32", "natural");
        add("cherry_leaves", "Cherry Leaves", "#e8b4c8", "natural");
        add("pale_oak_leaves", "Pale Oak Leaves", "#a8c090", "natural");
        add("sculk", "Sculk", "#0d1d24", "natural");
        add("sculk_catalyst", "Sculk Catalyst", "#0d1d24", "natural");
        add("sculk_vein", "Sculk Vein", "#052b33", "natural");
        add("sculk_sensor", "Sculk Sensor", "#074a5a", "natural");
        add("calibrated_sculk_sensor", "Calibrated Sculk Sensor", "#074a5a", "natural");
        add("sculk_shrieker", "Sculk Shrieker", "#0d1d24", "natural");
        add("hay_block", "Hay Bale", "#a68d26", "natural");
        add("honeycomb_block", "Honeycomb Block", "#e5961f", "natural");
        add("slime_block", "Slime Block", "#6fc25a", "natural");
        add("honey_block", "Honey Block", "#f9be3d", "natural");
        add("dried_kelp_block", "Dried Kelp Block", "#3d4d32", "natural");
        add("sponge", "Sponge", "#c2b83e", "natural");
        add("wet_sponge", "Wet Sponge", "#a9a832", "natural");
        add("melon", "Melon", "#6a9b2a", "natural");
        add("pumpkin", "Pumpkin", "#c77819", "natural");
        add("carved_pumpkin", "Carved Pumpkin", "#c77819", "natural");
        add("jack_o_lantern", "Jack o'Lantern", "#c77819", "natural");
        add("bone_block", "Bone Block", "#e1ddc9", "natural");
        add("cactus", "Cactus", "#5a8a32", "natural");
        add("sea_lantern", "Sea Lantern", "#a8c8c8", "natural");
        add("ochre_froglight", "Ochre Froglight", "#f9e7a0", "natural");
        add("verdant_froglight", "Verdant Froglight", "#a0f9a0", "natural");
        add("pearlescent_froglight", "Pearlescent Froglight", "#f0a0f0", "natural");
        add("mangrove_roots", "Mangrove Roots", "#4a3a2a", "natural");

        // ==================== FUNCTIONAL BLOCKS ====================
        add("crafting_table", "Crafting Table", "#9a6a3a", "functional");
        add("furnace", "Furnace", "#6a6a6a", "functional");
        add("blast_furnace", "Blast Furnace", "#5a5a5a", "functional");
        add("smoker", "Smoker", "#5a4a3a", "functional");
        add("cartography_table", "Cartography Table", "#5a4a3a", "functional");
        add("fletching_table", "Fletching Table", "#c8b77a", "functional");
        add("smithing_table", "Smithing Table", "#3a3a5a", "functional");
        add("loom", "Loom", "#9a7a5a", "functional");
        add("stonecutter", "Stonecutter", "#7a7a7a", "functional");
        add("grindstone", "Grindstone", "#7a7a7a", "functional");
        add("lectern", "Lectern", "#9a6a3a", "functional");
        add("composter", "Composter", "#6a5a3a", "functional");
        add("barrel", "Barrel", "#6a5a3a", "functional");
        add("beehive", "Beehive", "#9a7a3a", "functional");
        add("bee_nest", "Bee Nest", "#c9a03a", "functional");
        add("bookshelf", "Bookshelf", "#6a5a3a", "functional");
        add("chiseled_bookshelf", "Chiseled Bookshelf", "#9a6a3a", "functional");
        add("enchanting_table", "Enchanting Table", "#3a3a5a", "functional");
        add("brewing_stand", "Brewing Stand", "#5a5a5a", "functional");
        add("cauldron", "Cauldron", "#4a4a4a", "functional");
        add("anvil", "Anvil", "#4a4a4a", "functional");
        add("chipped_anvil", "Chipped Anvil", "#4a4a4a", "functional");
        add("damaged_anvil", "Damaged Anvil", "#4a4a4a", "functional");
        add("bell", "Bell", "#c9a03a", "functional");
        add("jukebox", "Jukebox", "#6a4a3a", "functional");
        add("note_block", "Note Block", "#6a4a3a", "functional");
        add("target", "Target", "#e0d0c0", "functional");
        add("lodestone", "Lodestone", "#8a8a8a", "functional");
        add("respawn_anchor", "Respawn Anchor", "#2a1a3a", "functional");
        add("decorated_pot", "Decorated Pot", "#9a6a4a", "functional");
        add("crafter", "Crafter", "#6a5a4a", "functional");
        add("trial_spawner", "Trial Spawner", "#4a5a6a", "functional");
        add("vault", "Vault", "#4a5a6a", "functional");
        add("heavy_core", "Heavy Core", "#4a4a4a", "functional");

        // ==================== REDSTONE ====================
        add("redstone_lamp", "Redstone Lamp", "#6a4a2a", "redstone");
        add("observer", "Observer", "#5a5a5a", "redstone");
        add("piston", "Piston", "#9a8a6a", "redstone");
        add("sticky_piston", "Sticky Piston", "#8a9a6a", "redstone");
        add("dispenser", "Dispenser", "#7a7a7a", "redstone");
        add("dropper", "Dropper", "#7a7a7a", "redstone");
        add("hopper", "Hopper", "#4a4a4a", "redstone");
        add("tnt", "TNT", "#c03030", "redstone");
        add("daylight_detector", "Daylight Detector", "#9a8a6a", "redstone");

        // ==================== NETHER BLOCKS ====================
        add("netherrack", "Netherrack", "#723232", "nether");
        add("nether_bricks", "Nether Bricks", "#2c161a", "nether");
        add("cracked_nether_bricks", "Cracked Nether Bricks", "#2c161a", "nether");
        add("chiseled_nether_bricks", "Chiseled Nether Bricks", "#2c161a", "nether");
        add("red_nether_bricks", "Red Nether Bricks", "#450709", "nether");
        add("soul_sand", "Soul Sand", "#513a2f", "nether");
        add("soul_soil", "Soul Soil", "#4b3a30", "nether");
        add("glowstone", "Glowstone", "#ab8654", "nether");
        add("shroomlight", "Shroomlight", "#f09848", "nether");
        add("magma_block", "Magma Block", "#8e4100", "nether");
        add("basalt", "Basalt", "#49494d", "nether");
        add("polished_basalt", "Polished Basalt", "#58585c", "nether");
        add("smooth_basalt", "Smooth Basalt", "#48484c", "nether");
        add("blackstone", "Blackstone", "#2a2328", "nether");
        add("polished_blackstone", "Polished Blackstone", "#353038", "nether");
        add("polished_blackstone_bricks", "Polished Blackstone Bricks", "#302a32", "nether");
        add("cracked_polished_blackstone_bricks", "Cracked Polished Blackstone Bricks", "#2a2428", "nether");
        add("chiseled_polished_blackstone", "Chiseled Polished Blackstone", "#353038", "nether");
        add("gilded_blackstone", "Gilded Blackstone", "#3a3238", "nether");
        add("ancient_debris", "Ancient Debris", "#5e4840", "nether");
        add("crimson_nylium", "Crimson Nylium", "#851818", "nether");
        add("warped_nylium", "Warped Nylium", "#167e74", "nether");
        add("nether_wart_block", "Nether Wart Block", "#730303", "nether");
        add("warped_wart_block", "Warped Wart Block", "#167e74", "nether");
        add("nether_gold_ore", "Nether Gold Ore", "#723232", "nether");
        add("nether_quartz_ore", "Nether Quartz Ore", "#723232", "nether");

        // ==================== END BLOCKS ====================
        add("end_stone", "End Stone", "#dbde9e", "end");
        add("end_stone_bricks", "End Stone Bricks", "#dae0a3", "end");
        add("purpur_block", "Purpur Block", "#a97fa9", "end");
        add("purpur_pillar", "Purpur Pillar", "#ab7fab", "end");
        add("end_rod", "End Rod", "#e0d8d0", "end");
        add("chorus_plant", "Chorus Plant", "#5a4a5a", "end");
        add("chorus_flower", "Chorus Flower", "#8a7a8a", "end");
        add("dragon_egg", "Dragon Egg", "#0c090f", "end");

        // ==================== SHULKER BOXES (17) ====================
        add("shulker_box", "Shulker Box", "#8b6a8b", "functional");
        add("white_shulker_box", "White Shulker Box", "#d9d9d9", "functional");
        add("orange_shulker_box", "Orange Shulker Box", "#f07613", "functional");
        add("magenta_shulker_box", "Magenta Shulker Box", "#bd44b3", "functional");
        add("light_blue_shulker_box", "Light Blue Shulker Box", "#3ab3da", "functional");
        add("yellow_shulker_box", "Yellow Shulker Box", "#f8c627", "functional");
        add("lime_shulker_box", "Lime Shulker Box", "#70b919", "functional");
        add("pink_shulker_box", "Pink Shulker Box", "#ed8dac", "functional");
        add("gray_shulker_box", "Gray Shulker Box", "#3e4447", "functional");
        add("light_gray_shulker_box", "Light Gray Shulker Box", "#8e8e86", "functional");
        add("cyan_shulker_box", "Cyan Shulker Box", "#158991", "functional");
        add("purple_shulker_box", "Purple Shulker Box", "#7b2fbe", "functional");
        add("blue_shulker_box", "Blue Shulker Box", "#35399d", "functional");
        add("brown_shulker_box", "Brown Shulker Box", "#724728", "functional");
        add("green_shulker_box", "Green Shulker Box", "#546d1b", "functional");
        add("red_shulker_box", "Red Shulker Box", "#a12722", "functional");
        add("black_shulker_box", "Black Shulker Box", "#141519", "functional");

        // ==================== BEDS (16) ====================
        add("white_bed", "White Bed", "#e9ecec", "functional");
        add("orange_bed", "Orange Bed", "#f07613", "functional");
        add("magenta_bed", "Magenta Bed", "#bd44b3", "functional");
        add("light_blue_bed", "Light Blue Bed", "#3ab3da", "functional");
        add("yellow_bed", "Yellow Bed", "#f8c627", "functional");
        add("lime_bed", "Lime Bed", "#70b919", "functional");
        add("pink_bed", "Pink Bed", "#ed8dac", "functional");
        add("gray_bed", "Gray Bed", "#3e4447", "functional");
        add("light_gray_bed", "Light Gray Bed", "#8e8e86", "functional");
        add("cyan_bed", "Cyan Bed", "#158991", "functional");
        add("purple_bed", "Purple Bed", "#7b2fbe", "functional");
        add("blue_bed", "Blue Bed", "#35399d", "functional");
        add("brown_bed", "Brown Bed", "#724728", "functional");
        add("green_bed", "Green Bed", "#546d1b", "functional");
        add("red_bed", "Red Bed", "#a12722", "functional");
        add("black_bed", "Black Bed", "#141519", "functional");

        // ==================== CANDLES (17) ====================
        add("candle", "Candle", "#d9c8a0", "functional");
        add("white_candle", "White Candle", "#d9d9d9", "functional");
        add("orange_candle", "Orange Candle", "#f07613", "functional");
        add("magenta_candle", "Magenta Candle", "#bd44b3", "functional");
        add("light_blue_candle", "Light Blue Candle", "#3ab3da", "functional");
        add("yellow_candle", "Yellow Candle", "#f8c627", "functional");
        add("lime_candle", "Lime Candle", "#70b919", "functional");
        add("pink_candle", "Pink Candle", "#ed8dac", "functional");
        add("gray_candle", "Gray Candle", "#3e4447", "functional");
        add("light_gray_candle", "Light Gray Candle", "#8e8e86", "functional");
        add("cyan_candle", "Cyan Candle", "#158991", "functional");
        add("purple_candle", "Purple Candle", "#7b2fbe", "functional");
        add("blue_candle", "Blue Candle", "#35399d", "functional");
        add("brown_candle", "Brown Candle", "#724728", "functional");
        add("green_candle", "Green Candle", "#546d1b", "functional");
        add("red_candle", "Red Candle", "#a12722", "functional");
        add("black_candle", "Black Candle", "#141519", "functional");

        // ==================== CARPETS (16) ====================
        add("white_carpet", "White Carpet", "#e9ecec", "wool");
        add("orange_carpet", "Orange Carpet", "#f07613", "wool");
        add("magenta_carpet", "Magenta Carpet", "#bd44b3", "wool");
        add("light_blue_carpet", "Light Blue Carpet", "#3ab3da", "wool");
        add("yellow_carpet", "Yellow Carpet", "#f8c627", "wool");
        add("lime_carpet", "Lime Carpet", "#70b919", "wool");
        add("pink_carpet", "Pink Carpet", "#ed8dac", "wool");
        add("gray_carpet", "Gray Carpet", "#3e4447", "wool");
        add("light_gray_carpet", "Light Gray Carpet", "#8e8e86", "wool");
        add("cyan_carpet", "Cyan Carpet", "#158991", "wool");
        add("purple_carpet", "Purple Carpet", "#7b2fbe", "wool");
        add("blue_carpet", "Blue Carpet", "#35399d", "wool");
        add("brown_carpet", "Brown Carpet", "#724728", "wool");
        add("green_carpet", "Green Carpet", "#546d1b", "wool");
        add("red_carpet", "Red Carpet", "#a12722", "wool");
        add("black_carpet", "Black Carpet", "#141519", "wool");

        // ==================== MISC BLOCKS ====================
        add("chest", "Chest", "#8a6a3a", "functional");
        add("ender_chest", "Ender Chest", "#1a2a3a", "functional");
        add("trapped_chest", "Trapped Chest", "#8a6a3a", "functional");
        add("ladder", "Ladder", "#9a7a4a", "functional");
        add("scaffolding", "Scaffolding", "#b09a5a", "functional");
        add("chain", "Chain", "#3a3a4a", "functional");
        add("iron_bars", "Iron Bars", "#8a8a8a", "functional");
        add("lightning_rod", "Lightning Rod", "#c06b4e", "functional");
        add("lantern", "Lantern", "#6a5a4a", "functional");
        add("soul_lantern", "Soul Lantern", "#4a5a6a", "functional");
        add("torch", "Torch", "#f9c03a", "functional");
        add("soul_torch", "Soul Torch", "#6ac0c0", "functional");
        add("campfire", "Campfire", "#8a6a3a", "functional");
        add("soul_campfire", "Soul Campfire", "#4a6a6a", "functional");
        add("end_portal_frame", "End Portal Frame", "#3a5a4a", "end");
        add("spawner", "Spawner", "#2a3a4a", "functional");
        add("infested_stone", "Infested Stone", "#7d7d7d", "stone");
        add("infested_cobblestone", "Infested Cobblestone", "#7f7f7f", "stone");
        add("infested_stone_bricks", "Infested Stone Bricks", "#7a7a7a", "stone");
        add("infested_deepslate", "Infested Deepslate", "#505050", "stone");
        add("sniffer_egg", "Sniffer Egg", "#6a8a5a", "natural");
        add("turtle_egg", "Turtle Egg", "#e0d8c0", "natural");
        add("frogspawn", "Frogspawn", "#5a4a3a", "natural");

        // ==================== RESIN BLOCKS ====================
        add("resin_block", "Resin Block", "#e07830", "natural");
        add("resin_bricks", "Resin Bricks", "#d06820", "stone");
        add("chiseled_resin_bricks", "Chiseled Resin Bricks", "#d06820", "stone");
        add("resin_clump", "Resin Clump", "#e07830", "natural");
        add("creaking_heart", "Creaking Heart", "#4a3a2a", "natural");
    }

    private static void initNonSolid() {
        // Ported exactly from blocks.js NON_SOLID_BLOCKS array
        Collections.addAll(NON_SOLID_BLOCKS,
            "torch", "soul_torch", "lantern", "soul_lantern", "chain", "iron_bars",
            "ladder", "scaffolding", "lightning_rod", "end_rod", "campfire", "soul_campfire",
            "brewing_stand", "cauldron", "anvil", "chipped_anvil", "damaged_anvil", "bell",
            "grindstone", "stonecutter", "lectern", "composter", "hopper", "chest",
            "ender_chest", "trapped_chest", "enchanting_table", "spawner", "end_portal_frame",
            "daylight_detector", "turtle_egg", "frogspawn", "sniffer_egg", "dragon_egg",
            // Candles
            "candle", "white_candle", "orange_candle", "magenta_candle", "light_blue_candle",
            "yellow_candle", "lime_candle", "pink_candle", "gray_candle", "light_gray_candle",
            "cyan_candle", "purple_candle", "blue_candle", "brown_candle", "green_candle",
            "red_candle", "black_candle",
            // Beds
            "white_bed", "orange_bed", "magenta_bed", "light_blue_bed", "yellow_bed",
            "lime_bed", "pink_bed", "gray_bed", "light_gray_bed", "cyan_bed",
            "purple_bed", "blue_bed", "brown_bed", "green_bed", "red_bed", "black_bed",
            // Carpets
            "white_carpet", "orange_carpet", "magenta_carpet", "light_blue_carpet",
            "yellow_carpet", "lime_carpet", "pink_carpet", "gray_carpet", "light_gray_carpet",
            "cyan_carpet", "purple_carpet", "blue_carpet", "brown_carpet", "green_carpet",
            "red_carpet", "black_carpet",
            // Misc non-solid
            "moss_carpet", "sculk_vein", "sculk_sensor", "calibrated_sculk_sensor",
            "sculk_shrieker", "chorus_plant", "chorus_flower", "cactus",
            "decorated_pot", "trial_spawner", "vault", "heavy_core",
            "resin_clump", "creaking_heart"
        );
    }

    private static void initDominantOverrides() {
        // Ported from gradient.js DOMINANT_COLOR_OVERRIDES
        // Format: id -> {dominantR, dominantG, dominantB, weight (0-255 mapped from 0-1)}
        DOMINANT_COLOR_OVERRIDES.put("pale_oak_log", new int[]{0xb9, 0xa3, 0x85, 166}); // weight 0.65
        DOMINANT_COLOR_OVERRIDES.put("stripped_pale_oak_log", new int[]{0xcd, 0xbf, 0xa8, 140}); // weight 0.55
    }

    /**
     * Returns all blocks excluding non-solid ones.
     */
    public static List<Entry> getFilteredBlocks() {
        if (filteredCache == null) {
            filteredCache = ALL_BLOCKS.stream()
                .filter(e -> !NON_SOLID_BLOCKS.contains(e.id()))
                .toList();
        }
        return filteredCache;
    }

    /**
     * Returns the perceived RGB for a block, applying dominant color overrides.
     */
    public static int[] getPerceivedRgb(Entry block) {
        int[] override = DOMINANT_COLOR_OVERRIDES.get(block.id());
        if (override == null) {
            return new int[]{block.r(), block.g(), block.b()};
        }
        double weight = override[3] / 255.0;
        return new int[]{
            (int) Math.round(block.r() + (override[0] - block.r()) * weight),
            (int) Math.round(block.g() + (override[1] - block.g()) * weight),
            (int) Math.round(block.b() + (override[2] - block.b()) * weight)
        };
    }

    private BlockColor() {}
}
