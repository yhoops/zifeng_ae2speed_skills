package org.zifeng.ae2skills.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;

/** Common task state contract implemented by each supported executor's accessor mixin. */
public interface JobAccess {
    Map<IPatternDetails, Object> zifeng$getTasks();
    ListCraftingInventory zifeng$getWaitingFor();
    CraftingLink zifeng$getLink();
}
