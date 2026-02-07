package com.mctools.commands;

import com.mctools.MCTools;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tab completer for MCTools commands.
 * 
 * <p>Provides intelligent tab completion for all shape commands,
 * including block suggestions and parameter hints.</p>
 * 
 * @author MCTools Team
 * @version 1.0.0
 */
public class MCToolsTabCompleter implements TabCompleter {

    private final MCTools plugin;
    
    // Shape commands organized by type
    private static final List<String> SHAPE_COMMANDS = Arrays.asList(
            // 2D Filled
            "cir", "sq", "rect", "ell", "poly", "star", "line", "spi",
            // 2D Hollow
            "hcir", "hsq", "hrect", "hell", "hpoly",
            // 3D Filled
            "sph", "dome", "cyl", "cone", "pyr", "arch", "tor", "wall", "hel",
            "ellipsoid", "tube", "capsule", "tree",
            // 3D Hollow
            "hsph", "hdome", "hcyl", "hcone", "hpyr", "harch", "htor",
            "hellipsoid", "hcapsule"
    );

    // Gradient commands (g-prefix on all shape commands + hollow variants)
    private static final List<String> GRADIENT_COMMANDS = Arrays.asList(
            // 2D Filled gradient
            "gcir", "gsq", "grect", "gell", "gpoly", "gstar", "gline", "gspi",
            // 2D Hollow gradient
            "ghcir", "ghsq", "ghrect", "ghell", "ghpoly",
            // 3D Filled gradient
            "gsph", "gdome", "gcyl", "gcone", "gpyr", "garch", "gtor", "gwall", "ghel",
            "gellipsoid", "gtube", "gcapsule",
            // 3D Hollow gradient
            "ghsph", "ghdome", "ghcyl", "ghcone", "ghpyr", "gharch", "ghtor",
            "ghellipsoid", "ghcapsule"
    );

    private static final List<String> ADMIN_COMMANDS = Arrays.asList(
            "help", "reload", "undo", "redo", "cancel", "pause", "resume", "info", "about", "version"
    );
    
    private static final List<String> CONTROL_COMMANDS = Arrays.asList(
            "cancel", "pause", "resume"
    );

    private static final List<String> COMMON_BLOCKS = Arrays.asList(
            "stone", "cobblestone", "stone_bricks", "oak_planks", "spruce_planks",
            "birch_planks", "dark_oak_planks", "bricks", "sandstone", "red_sandstone",
            "quartz_block", "smooth_quartz", "prismarine", "dark_prismarine",
            "nether_bricks", "red_nether_bricks", "end_stone_bricks", "purpur_block",
            "obsidian", "crying_obsidian", "blackstone", "polished_blackstone",
            "deepslate_bricks", "polished_deepslate", "copper_block", "cut_copper",
            "iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block",
            "glass", "white_stained_glass", "black_stained_glass", "gray_stained_glass",
            "white_concrete", "black_concrete", "gray_concrete", "light_gray_concrete",
            "white_wool", "black_wool", "glowstone", "sea_lantern", "shroomlight",
            "dirt", "grass_block", "sand", "gravel", "clay", "terracotta",
            "white_terracotta", "black_terracotta", "packed_ice", "blue_ice"
    );

    /**
     * Creates a new MCToolsTabCompleter instance.
     * 
     * @param plugin The plugin instance
     */
    public MCToolsTabCompleter(MCTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: shape/admin/gradient
            String input = args[0].toLowerCase();

            completions.addAll(filterStartsWith(SHAPE_COMMANDS, input));
            completions.addAll(filterStartsWith(GRADIENT_COMMANDS, input));
            completions.addAll(filterStartsWith(ADMIN_COMMANDS, input));

        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            if (subCmd.equals("tree")) {
                // Second argument for tree: wood type
                completions.addAll(filterStartsWith(Arrays.asList(
                    "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                    "mangrove", "cherry", "crimson", "warped"
                ), input));
            } else if (GRADIENT_COMMANDS.contains(subCmd)) {
                // Second argument for gradient: hex colors
                if (input.isEmpty() || input.startsWith("#")) {
                    completions.addAll(Arrays.asList("#ff0000,#0000ff", "#ff6b6b,#4ecdc4", "#ffffff,#000000",
                        "#ff0000,#00ff00,#0000ff", "#ff6b6b,#ffd93d,#6bcb77,#4d96ff"));
                }
            } else if (SHAPE_COMMANDS.contains(subCmd)) {
                // Second argument for shapes: block type
                completions.addAll(getBlockSuggestions(player, input));
            } else if (subCmd.equals("help")) {
                completions.addAll(filterStartsWith(SHAPE_COMMANDS, input));
            } else if (subCmd.equals("undo") || subCmd.equals("redo")) {
                completions.addAll(filterStartsWith(Arrays.asList("1", "5", "10", "25", "50", "100"), input));
            }

        } else if (args.length >= 3) {
            String subCmd = args[0].toLowerCase();
            String lastArg = args[args.length - 1].toLowerCase();
            String prevArg = args.length >= 2 ? args[args.length - 2].toLowerCase() : "";

            if (subCmd.equals("tree")) {
                // Tree command: key:value params and flags after wood type
                completions.addAll(filterStartsWith(Arrays.asList(
                    "seed:", "th:", "tr:", "bd:", "fd:", "fr:", "-roots", "-special"
                ), lastArg));
            } else if (GRADIENT_COMMANDS.contains(subCmd)) {
                // Gradient command: suggest flags or base shape params
                if (prevArg.equals("-dir")) {
                    completions.addAll(filterStartsWith(Arrays.asList("y", "x", "z", "radial"), lastArg));
                } else if (prevArg.equals("-interp")) {
                    completions.addAll(filterStartsWith(Arrays.asList("oklab", "lab", "rgb", "hsl"), lastArg));
                } else if (lastArg.startsWith("-")) {
                    completions.addAll(filterStartsWith(Arrays.asList("-dir", "-interp", "-unique"), lastArg));
                } else {
                    // Suggest shape parameters based on base shape
                    String baseCmd = subCmd.substring(1); // strip 'g'
                    // argIndex: args[2] is the first shape param (index 1)
                    int shapeArgIndex = args.length - 2; // -1 for cmd, -1 for colors
                    completions.addAll(getParameterSuggestions(baseCmd, shapeArgIndex));
                    // Also suggest flags
                    completions.addAll(Arrays.asList("-dir", "-interp", "-unique"));
                }
            } else {
                // Regular shape parameter suggestions
                completions.addAll(getParameterSuggestions(subCmd, args.length - 2));
            }
        }

        return completions;
    }

    /**
     * Gets block suggestions including common blocks and player inventory.
     */
    private List<String> getBlockSuggestions(Player player, String input) {
        Set<String> suggestions = new LinkedHashSet<>();
        
        // Add blocks from player's inventory first
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().isBlock()) {
                String name = item.getType().name().toLowerCase();
                if (name.startsWith(input)) {
                    suggestions.add(name);
                }
            }
        }
        
        // Add common blocks
        for (String block : COMMON_BLOCKS) {
            if (block.startsWith(input)) {
                suggestions.add(block);
            }
        }
        
        // Add all matching materials if input is specific enough
        if (input.length() >= 2) {
            for (Material mat : Material.values()) {
                if (mat.isBlock() && mat.name().toLowerCase().startsWith(input)) {
                    suggestions.add(mat.name().toLowerCase());
                    if (suggestions.size() > 30) break;
                }
            }
        }
        
        return new ArrayList<>(suggestions);
    }

    /**
     * Gets parameter suggestions based on shape and argument position.
     */
    private List<String> getParameterSuggestions(String shape, int argIndex) {
        List<String> suggestions = new ArrayList<>();
        
        // Common radius/size suggestions
        List<String> radiusSuggestions = Arrays.asList("5", "10", "15", "20", "25", "30");
        List<String> heightSuggestions = Arrays.asList("5", "10", "15", "20", "30", "50");
        List<String> thicknessSuggestions = Arrays.asList("1", "2", "3", "4", "5");
        List<String> sidesSuggestions = Arrays.asList("3", "4", "5", "6", "8", "10", "12");
        List<String> turnsSuggestions = Arrays.asList("1", "2", "3", "4", "5");

        switch (shape) {
            // Circle
            case "cir" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
            }
            case "hcir" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(thicknessSuggestions);
            }
            
            // Square
            case "sq" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
            }
            case "hsq" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(thicknessSuggestions);
            }
            
            // Rectangle
            case "rect" -> {
                if (argIndex <= 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(Arrays.asList("0", "2", "3", "5"));
            }
            case "hrect" -> {
                if (argIndex <= 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(Arrays.asList("0", "2", "3", "5"));
                else if (argIndex == 4) suggestions.addAll(thicknessSuggestions);
            }
            
            // Ellipse
            case "ell" -> {
                if (argIndex <= 2) suggestions.addAll(radiusSuggestions);
            }
            case "hell" -> {
                if (argIndex <= 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Polygon
            case "poly" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(sidesSuggestions);
            }
            case "hpoly" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(sidesSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Star
            case "star" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(thicknessSuggestions);
            }
            
            // Line
            case "line" -> {
                if (argIndex == 1) suggestions.addAll(Arrays.asList("10", "20", "30", "50", "100"));
                else if (argIndex == 2) suggestions.addAll(thicknessSuggestions);
            }
            
            // Spiral
            case "spi" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(turnsSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Sphere
            case "sph" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
            }
            case "hsph" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(thicknessSuggestions);
            }
            
            // Dome
            case "dome" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
            }
            case "hdome" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(thicknessSuggestions);
            }
            
            // Cylinder
            case "cyl" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
            }
            case "hcyl" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Cone
            case "cone" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
            }
            case "hcone" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Pyramid
            case "pyr" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
            }
            case "hpyr" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Arch
            case "arch" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(Arrays.asList("3", "5", "7", "10"));
            }
            case "harch" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(Arrays.asList("3", "5", "7", "10"));
                else if (argIndex == 4) suggestions.addAll(thicknessSuggestions);
            }
            
            // Torus
            case "tor" -> {
                if (argIndex == 1) suggestions.addAll(Arrays.asList("10", "15", "20", "25"));
                else if (argIndex == 2) suggestions.addAll(Arrays.asList("3", "4", "5", "6", "8"));
            }
            case "htor" -> {
                if (argIndex == 1) suggestions.addAll(Arrays.asList("10", "15", "20", "25"));
                else if (argIndex == 2) suggestions.addAll(Arrays.asList("3", "4", "5", "6", "8"));
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Wall
            case "wall" -> {
                if (argIndex == 1) suggestions.addAll(Arrays.asList("10", "20", "30", "50"));
                else if (argIndex == 2) suggestions.addAll(heightSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
            
            // Helix
            case "hel" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(turnsSuggestions);
                else if (argIndex == 4) suggestions.addAll(thicknessSuggestions);
            }

            // Ellipsoid
            case "ellipsoid" -> {
                if (argIndex <= 3) suggestions.addAll(radiusSuggestions);
            }
            case "hellipsoid" -> {
                if (argIndex <= 3) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 4) suggestions.addAll(thicknessSuggestions);
            }

            // Tube
            case "tube" -> {
                if (argIndex == 1) suggestions.addAll(heightSuggestions);
                else if (argIndex == 2) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 3) suggestions.addAll(Arrays.asList("3", "4", "5", "6", "8"));
            }

            // Capsule
            case "capsule" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(heightSuggestions);
            }
            case "hcapsule" -> {
                if (argIndex == 1) suggestions.addAll(radiusSuggestions);
                else if (argIndex == 2) suggestions.addAll(heightSuggestions);
                else if (argIndex == 3) suggestions.addAll(thicknessSuggestions);
            }
        }

        return suggestions;
    }

    /**
     * Filters a list to only include items starting with the given prefix.
     */
    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
