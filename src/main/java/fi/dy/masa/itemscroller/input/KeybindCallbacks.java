package fi.dy.masa.itemscroller.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;

import malilib.config.option.HotkeyConfig;
import malilib.gui.util.GuiUtils;
import malilib.input.ActionResult;
import malilib.input.KeyAction;
import malilib.input.KeyBind;
import malilib.input.callback.HotkeyCallback;
import malilib.util.game.wrap.GameWrap;
import malilib.util.inventory.InventoryScreenUtils;
import fi.dy.masa.itemscroller.ItemScroller;
import fi.dy.masa.itemscroller.config.Actions;
import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.itemscroller.config.Hotkeys;
import fi.dy.masa.itemscroller.recipes.CraftingHandler;
import fi.dy.masa.itemscroller.recipes.CraftingRecipe;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import fi.dy.masa.itemscroller.util.InputUtils;
import fi.dy.masa.itemscroller.util.InventoryUtils;
import fi.dy.masa.itemscroller.util.MoveAction;

public class KeybindCallbacks implements HotkeyCallback
{
    public static final KeybindCallbacks INSTANCE = new KeybindCallbacks();

    public void setCallbacks()
    {
        Configs.Lists.CRAFTING_SCREENS.setValueChangeCallback((n, o) -> CraftingHandler.updateGridDefinitions(n));
        Configs.Lists.CRAFTING_SCREENS.setValueLoadCallback(CraftingHandler::updateGridDefinitions);

        Hotkeys.OPEN_CONFIG_SCREEN.createCallbackForAction(Actions.OPEN_CONFIG_SCREEN);

        for (HotkeyConfig hotkey : Hotkeys.HOTKEY_LIST)
        {
            if (hotkey != Hotkeys.OPEN_CONFIG_SCREEN)
            {
                hotkey.getKeyBind().setCallback(this);
            }
        }
    }

    @Override
    public ActionResult onKeyAction(KeyAction action, KeyBind key)
    {
        Minecraft mc = GameWrap.getClient();

        if (mc.player == null)
        {
            return ActionResult.FAIL;
        }

        if ((GuiUtils.getCurrentScreen() instanceof GuiContainer) == false ||
            Configs.Toggles.MOD_FEATURES_ENABLED.getBooleanValue() == false)
        {
            return ActionResult.FAIL;
        }

        GuiContainer gui = (GuiContainer) GuiUtils.getCurrentScreen();
        Slot slot = InventoryScreenUtils.getSlotUnderMouse(gui);
        RecipeStorage recipes = RecipeStorage.INSTANCE;
        MoveAction moveAction = InputUtils.getDragMoveAction(key);

        if (slot != null)
        {
            if (moveAction != MoveAction.NONE)
            {
                int mx = InputUtils.getMouseX();
                int my = InputUtils.getMouseY();
                return InventoryUtils.dragMoveItems(gui, moveAction, true, mx, my) ? ActionResult.SUCCESS : ActionResult.FAIL;
            }
            else if (key == Hotkeys.MOVE_EVERYTHING.getKeyBind())
            {
                InventoryUtils.tryMoveStacks(slot, gui, false, true, false);
                return ActionResult.SUCCESS;
            }
            else if (key == Hotkeys.DROP_ALL_MATCHING.getKeyBind())
            {
                if (Configs.Toggles.DROP_MATCHING.getBooleanValue() &&
                    Configs.Lists.GUI_BLACKLIST.getValue().contains(gui.getClass().getName()) == false &&
                    slot.getHasStack())
                {
                    InventoryUtils.dropStacks(gui, slot.getStack(), slot, true);
                    return ActionResult.SUCCESS;
                }
            }
            else if (key == Hotkeys.MOVE_STACK_TO_OFFHAND.getKeyBind())
            {
                // Swap the hovered stack to the Offhand
                if ((gui instanceof GuiInventory) && slot != null)
                {
                    InventoryUtils.swapSlots(gui, slot.slotNumber, 45);
                    return ActionResult.SUCCESS;
                }
            }
        }

        if (key == Hotkeys.CRAFT_EVERYTHING.getKeyBind())
        {
            return InventoryUtils.craftEverythingPossibleWithCurrentRecipe(recipes.getSelectedRecipe(), gui) ? ActionResult.SUCCESS : ActionResult.FAIL;
        }
        else if (key == Hotkeys.THROW_CRAFT_RESULTS.getKeyBind())
        {
            InventoryUtils.throwAllCraftingResultsToGround(recipes.getSelectedRecipe(), gui);
            return ActionResult.SUCCESS;
        }
        else if (key == Hotkeys.MOVE_CRAFT_RESULTS.getKeyBind())
        {
            InventoryUtils.moveAllCraftingResultsToOtherInventory(recipes.getSelectedRecipe(), gui);
            return ActionResult.SUCCESS;
        }
        else if (key == Hotkeys.STORE_RECIPE.getKeyBind())
        {
            return recipes.storeCraftingRecipeToCurrentSelection(slot, gui, true) ? ActionResult.SUCCESS : ActionResult.FAIL;
        }
        else if (key == Hotkeys.VILLAGER_TRADE_FAVORITES.getKeyBind())
        {
            return InventoryUtils.villagerTradeEverythingPossibleWithAllFavoritedTrades() ? ActionResult.SUCCESS : ActionResult.FAIL;
        }
        else if (key == Hotkeys.SLOT_DEBUG.getKeyBind())
        {
            if (slot != null)
            {
                InventoryUtils.debugPrintSlotInfo(gui, slot);
            }
            else
            {
                ItemScroller.LOGGER.info("GUI class: {}", gui.getClass().getName());
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    public void onTick()
    {
        Minecraft mc = GameWrap.getClient();

        if (mc.player != null &&
            Configs.Toggles.MOD_FEATURES_ENABLED.getBooleanValue() &&
            GuiUtils.getCurrentScreen() instanceof GuiContainer &&
            (GuiUtils.getCurrentScreen() instanceof GuiContainerCreative) == false &&
            Configs.Lists.GUI_BLACKLIST.getValue().contains(GuiUtils.getCurrentScreen().getClass().getName()) == false &&
            Hotkeys.MASS_CRAFT.getKeyBind().isKeyBindHeld())
        {
            GuiContainer gui = (GuiContainer) GuiUtils.getCurrentScreen();
            Slot outputSlot = CraftingHandler.getFirstCraftingOutputSlotForGui(gui);

            if (outputSlot != null)
            {
                CraftingRecipe recipe = RecipeStorage.INSTANCE.getSelectedRecipe();

                InventoryUtils.tryClearCursor(gui, mc);
                InventoryUtils.throwAllCraftingResultsToGround(recipe, gui);
                InventoryUtils.tryMoveItemsToFirstCraftingGrid(recipe, gui, true);

                int failsafe = 0;

                while (++failsafe < 40 && InventoryUtils.areStacksEqual(outputSlot.getStack(), recipe.getResult()))
                {
                    if (Configs.Generic.CARPET_CTRL_Q_CRAFTING.getBooleanValue())
                    {
                        InventoryUtils.dropStack(gui, outputSlot.slotNumber);
                    }
                    else
                    {
                        InventoryUtils.dropStacksWhileHasItem(gui, outputSlot.slotNumber, recipe.getResult());
                    }

                    InventoryUtils.tryClearCursor(gui, mc);
                    InventoryUtils.throwAllCraftingResultsToGround(recipe, gui);
                    InventoryUtils.tryMoveItemsToFirstCraftingGrid(recipe, gui, true);
                }
            }
        }
    }
}
