package fi.dy.masa.itemscroller.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.annotation.Nonnull;

import fi.dy.masa.itemscroller.recipes.RecipePattern;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.screen.slot.Slot;

import fi.dy.masa.itemscroller.ItemScroller;
import fi.dy.masa.itemscroller.Reference;
import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.itemscroller.util.Constants;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class RecipeDataStorage
{
    private static final int MAX_PAGES = 8;         // 8 Pages of 18 = 144 total slots
    private static final int MAX_RECIPES = 18;      // 8 Pages of 18 = 144 total slots
    private static final RecipeDataStorage INSTANCE = new RecipeDataStorage(MAX_RECIPES * MAX_PAGES);
    private final RecipePattern[] recipes;
    private int selected;
    private boolean dirty;

    public static RecipeDataStorage getInstance()
    {
        return INSTANCE;
    }

    public RecipeDataStorage(int recipeCount)
    {
        this.recipes = new RecipePattern[recipeCount];
        this.initRecipes();
    }

    public void reset(boolean isLogout)
    {
        if (isLogout)
        {
            //ItemScroller.printDebug("RecipeDataStorage#reset() - log-out");
            this.clearRecipes();
        }
        else
        {
            //ItemScroller.printDebug("RecipeDataStorage#reset() - dimension change or log-in");
        }
    }

    private void initRecipes()
    {
        for (int i = 0; i < this.recipes.length; i++)
        {
            this.recipes[i] = new RecipePattern();
        }
    }

    private void clearRecipes()
    {
        for (int i = 0; i < this.recipes.length; i++)
        {
            this.clearRecipe(i);
        }
    }

    public int getSelection()
    {
        return this.selected;
    }

    public void changeSelectedRecipe(int index)
    {
        if (index >= 0 && index < this.recipes.length)
        {
            this.selected = index;
            this.dirty = true;
        }
    }

    public void scrollSelection(boolean forward)
    {
        this.changeSelectedRecipe(this.selected + (forward ? 1 : -1));
    }

    public int getFirstVisibleRecipeId()
    {
        return this.getCurrentRecipePage() * this.getRecipeCountPerPage();
    }

    public int getTotalRecipeCount()
    {
        return this.recipes.length;
    }

    public int getRecipeCountPerPage()
    {
        return MAX_RECIPES;
    }

    public int getCurrentRecipePage()
    {
        return this.getSelection() / this.getRecipeCountPerPage();
    }

    /**
     * Returns the recipe for the given index.
     * If the index is invalid, then the first recipe is returned, instead of null.
     */
    @Nonnull
    public RecipePattern getRecipe(int index)
    {
        if (index >= 0 && index < this.recipes.length)
        {
            return this.recipes[index];
        }

        return this.recipes[0];
    }

    @Nonnull
    public RecipePattern getSelectedRecipe()
    {
        return this.getRecipe(this.getSelection());
    }

    public void storeCraftingRecipeToCurrentSelection(Slot slot, HandledScreen<?> gui, boolean clearIfEmpty)
    {
        this.storeCraftingRecipe(this.getSelection(), slot, gui, clearIfEmpty);
    }

    public void storeCraftingRecipe(int index, Slot slot, HandledScreen<?> gui, boolean clearIfEmpty)
    {
        this.getRecipe(index).storeCraftingRecipe(slot, gui, clearIfEmpty);
        this.dirty = true;
    }

    public void clearRecipe(int index)
    {
        this.getRecipe(index).clearRecipe();
        this.dirty = true;
    }

    private void readFromNBT(NbtCompound nbt)
    {
        if (nbt == null || !nbt.contains("Recipes", Constants.NBT.TAG_LIST))
        {
            return;
        }

        for (int i = 0; i < this.recipes.length; i++)
        {
            this.recipes[i].clearRecipe();
        }

        NbtList tagList = nbt.getList("Recipes", Constants.NBT.TAG_COMPOUND);
        int count = tagList.size();

        for (int i = 0; i < count; i++)
        {
            NbtCompound tag = tagList.getCompound(i);

            int index = tag.getByte("RecipeIndex");

            if (index >= 0 && index < this.recipes.length)
            {
                this.recipes[index].readFromNBT(tag);
            }
        }

        this.changeSelectedRecipe(nbt.getByte("Selected"));
    }

    private NbtCompound writeToNBT()
    {
        NbtList tagRecipes = new NbtList();
        NbtCompound nbt = new NbtCompound();

        for (int i = 0; i < this.recipes.length; i++)
        {
            if (this.recipes[i].isValid())
            {

                NbtCompound tag = this.recipes[i].writeToNBT();
                tag.putByte("RecipeIndex", (byte) i);
                tagRecipes.add(tag);
            }
        }

        nbt.put("Recipes", tagRecipes);
        nbt.putByte("Selected", (byte) this.selected);

        return nbt;
    }

    private String getFileName()
    {
        if (!Configs.Generic.SCROLL_CRAFT_RECIPE_FILE_GLOBAL.getBooleanValue())
        {
            String worldName = StringUtils.getWorldOrServerName();

            if (worldName != null)
            {
                return "recipes_" + worldName + ".nbt";
            }
        }

        return "recipes.nbt";
    }

    private File getSaveDir()
    {
        return new File(FileUtils.getMinecraftDirectory(), Reference.MOD_ID);
    }

    public void readFromDisk()
    {
        try
        {
            File saveDir = this.getSaveDir();

            if (saveDir != null)
            {
                File file = new File(saveDir, this.getFileName());

                if (file.exists() && file.isFile() && file.canRead())
                {
                    this.initRecipes();

                    FileInputStream is = new FileInputStream(file);
                    this.readFromNBT(NbtIo.readCompressed(is, NbtSizeTracker.ofUnlimitedBytes()));
                    is.close();
                    ItemScroller.printDebug("RecipeDataStorage#readFromDisk(): Read recipes from file '{}'", file.getPath());
                }
            }
        }
        catch (Exception e)
        {
            ItemScroller.logger.warn("RecipeDataStorage#readFromDisk(): Failed to read recipes from file", e);
        }
    }

    public void writeToDisk()
    {
        if (this.dirty)
        {
            try
            {
                File saveDir = this.getSaveDir();

                if (!saveDir.exists())
                {
                    if (!saveDir.mkdirs())
                    {
                        ItemScroller.logger.warn("RecipeDataStorage#writeToDisk(): Failed to create the recipe storage directory '{}'", saveDir.getPath());
                        return;
                    }
                }

                File fileTmp  = new File(saveDir, this.getFileName() + ".tmp");
                File fileReal = new File(saveDir, this.getFileName());
                FileOutputStream os = new FileOutputStream(fileTmp);
                NbtIo.writeCompressed(this.writeToNBT(), os);
                os.close();

                if (fileReal.exists())
                {
                    if (!fileReal.delete())
                    {
                        ItemScroller.logger.warn("RecipeDataStorage#writeToDisk(): failed to delete file {} ", fileReal.getName());
                    }
                }

                if (!fileTmp.renameTo(fileReal))
                {
                    ItemScroller.logger.warn("RecipeDataStorage#writeToDisk(): failed to rename file {} ", fileTmp.getName());
                }
                this.dirty = false;
            }
            catch (Exception e)
            {
                ItemScroller.logger.warn("RecipeDataStorage#writeToDisk(): Failed to write recipes to file!", e);
            }
        }
    }
}
