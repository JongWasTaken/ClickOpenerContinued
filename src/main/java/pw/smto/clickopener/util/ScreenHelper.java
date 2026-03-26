package pw.smto.clickopener.util;

import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import pw.smto.clickopener.ClickOpenerMod;
import pw.smto.clickopener.api.OpenContext;
import pw.smto.clickopener.api.Opener;
import pw.smto.clickopener.api.OpenerRegistry;
import pw.smto.clickopener.impl.BlockScreenOpener;
import pw.smto.clickopener.impl.ClickContext;
import pw.smto.clickopener.impl.ItemScreenOpener;
import pw.smto.clickopener.interfaces.ClosePacketSkipper;

public class ScreenHelper {
	private ScreenHelper() {}

	private static boolean openScreenFromContext(OpenContext<?, ?> context) {
		var previous = context.player().containerMenu;

		//The cursor stack will be restored later
		previous.setCarried(ItemStack.EMPTY);
		previous.sendAllDataToRemote();	//also reverts picking up the item

		//Open the inventory (skip the close packet to prevent the cursor position from resetting)
		((ClosePacketSkipper)context.player()).clickopener$setSkipClosePacket(true);
		context.openerConsumer(Opener::preOpen);
		var result = context.openerFunction(Opener::open);
		var openedScreen = context.player().containerMenu != previous;
		((ClosePacketSkipper)context.player()).clickopener$setSkipClosePacket(false);

		if (openedScreen) {
			//context.player().currentScreenHandler.enableSyncing();
			context.openerConsumer(Opener::postOpen);
		}

		//Restore the cursor stack and let player know
		context.player().containerMenu.setCarried(context.getCursorStack());
		context.player().containerMenu.sendAllDataToRemote();

		return result.consumesAction();
	}

	public static boolean openScreen(ClickContext context) {
		if (!ClickOpenerMod.CONFIG.isUsageInChestScreenAllowed()) {
			if (context.player().containerMenu instanceof ChestMenu) {
				return false;
			}
		}

		if (!ClickOpenerMod.PLAYER_CONFIGS.isClickTypeAllowed(context.player(), context.clickType()) || !ClickOpenerMod.CONFIG.isAllowed(context.initialStack().getItem())) {
			return false;
		}

		var opener = OpenerRegistry.get(context.initialStack().getItem());
		if (opener == null) {
			if (context.initialStack().getItem() instanceof BlockItem) {
				opener = BlockScreenOpener.DEFAULT_OPENER;
			} else {
				opener = ItemScreenOpener.DEFAULT_OPENER;
			}
		}

		try {
			return ScreenHelper.openScreenFromContext(opener.mutateContext(context));
		} catch (ItemOpenException e) {
			ClickOpenerMod.LOGGER.warn("Error opening item:", e);
		}
		return false;
	}
}
