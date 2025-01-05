package pw.smto.clickopener.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pw.smto.clickopener.api.OpenContext;
import pw.smto.clickopener.api.Opener;
import pw.smto.clickopener.interfaces.OpenContextHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;

@Mixin(ScreenHandler.class)
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

	@SuppressWarnings("unused")
	@Inject(at = @At("RETURN"), method = "onClosed")
	private void clickopener$onClose(PlayerEntity player, CallbackInfo info) {
		if (this.clickopener$hasOpenContext()) {
            this.clickopener$openContext.openerConsumer(Opener::onClose);
            this.clickopener$openContext = null;
		}
	}
}
