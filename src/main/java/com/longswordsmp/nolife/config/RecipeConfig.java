package com.longswordsmp.nolife.config;

import java.util.List;
import java.util.Map;

/**
 * A parsed shaped-crafting recipe from config.yml.
 *
 * @param enabled     whether the recipe should be registered
 * @param shape       1-3 rows, each 1-3 characters (space = empty slot)
 * @param ingredients maps a shape character to a Bukkit material name
 */
public record RecipeConfig(boolean enabled, List<String> shape, Map<Character, String> ingredients) {
}
