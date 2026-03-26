package pw.smto.clickopener.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pw.smto.clickopener.api.ClickType;
import pw.smto.clickopener.impl.ClickContext;
import pw.smto.clickopener.interfaces.Openable;
import pw.smto.clickopener.util.ScreenHelper;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Shadow
    public ServerPlayer player;

	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;stateId()I", shift = At.Shift.BEFORE), method = "handleContainerClick", cancellable = true)
	public void clickopener_onClickSlot(ServerboundContainerClickPacket packet, CallbackInfo info) {
		var slotIndex = packet.slotNum();
		if (slotIndex==AbstractContainerMenu.SLOT_CLICKED_OUTSIDE || slotIndex==-1) {
			//use Minecraft default handling
			return;
		}

		var slot = this.player.containerMenu.getSlot(slotIndex);
		var stack = slot.getItem();
		if (stack!=null && ((Openable)(Object)stack).clickopener$hasCloser()) {
			//Do nothing/revert picking up the item
            this.player.containerMenu.sendAllDataToRemote();
			info.cancel();
			return;
		}

		var clickType = ClickType.convert(packet.containerInput(), packet.buttonNum(), slotIndex);
		if (ClickType.NONE == clickType) {
			//use Minecraft default handling
			return;
		}

		for (var hand : InteractionHand.values()) {
			if (ScreenHelper.openScreen(new ClickContext(this.player, hand, slot.container, slot.getContainerSlot(), clickType, this.player.containerMenu.getCarried(), stack))) {
				//Successfully opened, so don't do anything else
				info.cancel();
				return;
			}//else Minecraft default handling
		}
	}	
}