package pw.smto.clickopener.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import pw.smto.clickopener.interfaces.ClosePacketSkipper;

@SuppressWarnings("java:S2160")
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin extends Player implements ClosePacketSkipper {
	protected ServerPlayerEntityMixin(Level world, GameProfile gameProfile) {
		super(world, gameProfile);
	}

	@Unique
	@SuppressWarnings("java:S116")
	private boolean clickopener$skipClosePacket;

	@Override
	public void clickopener$setSkipClosePacket(boolean skipClosePacket) {
        this.clickopener$skipClosePacket = skipClosePacket;
	}

	@Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
	private void clickopener$skipClosePacket(CallbackInfo info) {
		if (this.clickopener$skipClosePacket) {
            this.doCloseContainer();
			info.cancel();
		}
	}
}
