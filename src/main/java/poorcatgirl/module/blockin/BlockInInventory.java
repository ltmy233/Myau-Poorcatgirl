package poorcatgirl.module.blockin;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;

public final class BlockInInventory {
    private final Minecraft mc=Minecraft.getMinecraft();
    private int intended=-1, owned=-1;
    private EntityPlayer owner;

    public void reset() { release(); }
    public int intendedSlot() { return intended>=0 ? intended : mc.thePlayer.inventory.currentItem; }
    public void setIntent(int slot) { if(slot>=0 && slot<9)intended=slot; }
    public boolean ownsSlot() { return owned>=0; }
    public boolean safeContainer() {
        return mc.thePlayer!=null && mc.currentScreen==null
                && mc.thePlayer.openContainer==mc.thePlayer.inventoryContainer
                && mc.thePlayer.inventory.getItemStack()==null;
    }
    public void tick() {
        if (mc.thePlayer==null || owner!=null && owner!=mc.thePlayer) {owned=-1;intended=-1;owner=null;return;}
        if (owned>=0 && mc.thePlayer.inventory.currentItem!=owned) {
            intended=mc.thePlayer.inventory.currentItem;owned=-1;
        }
    }
    public int acquire(int inventorySlot) {
        if (!safeContainer() || inventorySlot<0 || inventorySlot>=9)return -1;
        if(owner!=null && owner!=mc.thePlayer) {owned=-1;intended=-1;}
        ItemStack stack=mc.thePlayer.inventory.getStackInSlot(inventorySlot);
        if (stack==null || stack.stackSize<=0)return -1;
        if (intended<0)intended=mc.thePlayer.inventory.currentItem;
        owned=inventorySlot;
        owner=mc.thePlayer;
        if (mc.thePlayer.inventory.currentItem!=inventorySlot) {
            mc.thePlayer.inventory.currentItem=inventorySlot;
        }
        return inventorySlot;
    }
    public void release() {
        if(mc.thePlayer!=null && owner==mc.thePlayer && owned>=0 && intended>=0
                && mc.thePlayer.inventory.currentItem==owned && mc.thePlayer.inventory.currentItem!=intended) {
            mc.thePlayer.inventory.currentItem=intended;
        }
        owned=-1;intended=-1;owner=null;
    }
}
