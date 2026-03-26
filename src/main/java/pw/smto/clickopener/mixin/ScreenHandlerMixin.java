package pw.smto.clickopener.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pw.smto.clickopener.api.OpenContext;
import pw.smto.clickopener.api.Opener;
import pw.smto.clickopener.interfaces.OpenContextHolder;
import pw.smto.clickopener.interfaces.Openable;

@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerMixin implements OpenContextHolder {
	@Unique
	@SuppressWarnings("java:S116")
	private OpenContext<?, ?> clickopener$openContext;

	@Override
	public void clickopener$setOpenContext(OpenContext<?, ?> openContext) {
        this.clickopener$openContext = openContext;
	}

	@Override
	public boolean clickopener$hasOpenContext() {
		return this.clickopener$openContext != null;
	}

	@Shadow
	public final NonNullList<Slot> slots = NonNullList.create();

	@SuppressWarnings("unused")
	@Inject(at = @At("RETURN"), method = "removed")
	private void clickopener$onClose(Player player, CallbackInfo info) {
		if (this.clickopener$hasOpenContext()) {
            this.clickopener$openContext.openerConsumer(Opener::onClose);
            this.clickopener$openContext = null;
		}
	}

	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"), method = "doClick", cancellable = true)
	private void internalSlotClickHook(int slotIndex, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo ci) {
		if (containerInput == ContainerInput.SWAP) {
			ItemStack sourceStack = player.getInventory().getItem(buttonNum);
			Slot slot = this.slots.get(slotIndex);
			ItemStack targetStack = slot.getItem();
			//ClickOpenerMod.LOGGER.warn("Swap about to occur");
			//ClickOpenerMod.LOGGER.warn("Source has closer: " + ((Openable)(Object)sourceStack).clickopener$hasCloser());
			//ClickOpenerMod.LOGGER.warn("Target has closer: " + ((Openable)(Object)targetStack).clickopener$hasCloser());
			if (((Openable)(Object)sourceStack).clickopener$hasCloser() || ((Openable)(Object)targetStack).clickopener$hasCloser()) {
				ci.cancel();
				return;
			}
		}
	}
}
