package pw.smto.clickopener.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pw.smto.clickopener.api.OpenContext;
import pw.smto.clickopener.api.Opener;
import pw.smto.clickopener.interfaces.OpenContextHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import pw.smto.clickopener.interfaces.Openable;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin implements OpenContextHolder {
	@Unique
	@SuppressWarnings("java:S116")
	private OpenContext<?, ?> clickopener$openContext;

	@Override
	public void clickopener$setOpenContext(OpenContext<?, ?> openContext) {
		clickopener$openContext = openContext;
	}

	@Override
	public boolean clickopener$hasOpenContext() {
		return clickopener$openContext != null;
	}

	@Shadow
	public final DefaultedList<Slot> slots = DefaultedList.of();

	@SuppressWarnings("unused")
	@Inject(at = @At("RETURN"), method = "onClosed")
	private void clickopener$onClose(PlayerEntity player, CallbackInfo info) {
		if (clickopener$hasOpenContext()) {
			clickopener$openContext.openerConsumer(Opener::onClose);
			clickopener$openContext = null;
		}
	}

	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getStack()Lnet/minecraft/item/ItemStack;"), method = "internalOnSlotClick", cancellable = true)
	private void internalSlotClickHook(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		if (actionType == SlotActionType.SWAP) {
			ItemStack sourceStack = player.getInventory().getStack(button);
			Slot slot = this.slots.get(slotIndex);
			ItemStack targetStack = slot.getStack();
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
