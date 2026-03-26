package pw.smto.clickopener.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import pw.smto.clickopener.interfaces.UseAllower;

@Pseudo
@Mixin(targets = "net.additionz.misc.FletchingScreenHandler")
public abstract class ForcedScreenHandlerMixin extends AbstractContainerMenu implements UseAllower {
	@Unique
	@SuppressWarnings("java:S116")
	private boolean clickopener$isAllowed;

	protected ForcedScreenHandlerMixin(MenuType<?> type, int syncId) {
		super(type, syncId);
	}

	@Override
	public void clickopener$allowUse() {
        this.clickopener$isAllowed = true;
	}

	@Override
	public boolean clickopener$isUseAllowed() {
		return this.clickopener$isAllowed;
	}

	@SuppressWarnings("unused")
	@Inject(method = "canUse(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
	public void clickOpener$onCanUse(Player player, CallbackInfoReturnable<Boolean> info) {
		if (this.clickopener$isUseAllowed()) info.setReturnValue(true);
	}
}
