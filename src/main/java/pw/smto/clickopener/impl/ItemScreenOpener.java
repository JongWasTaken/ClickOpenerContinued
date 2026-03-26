package pw.smto.clickopener.impl;

import net.minecraft.world.InteractionResult;
import pw.smto.clickopener.api.Opener;

public interface ItemScreenOpener extends Opener<ItemScreenOpener, ItemOpenContext> {
	ItemScreenOpener DEFAULT_OPENER = new ItemScreenOpener() {
	};

	@Override
	default ItemOpenContext mutateContext(ClickContext context) {
		return new ItemOpenContext(context, this);
	}

	@Override
	default InteractionResult open(ItemOpenContext context) {
		return context.runWithStackInHand(context::getStack, context::setStack, stack -> {
			var result = stack.use(context.player().level(), context.player(), context.hand());
			if (result instanceof InteractionResult.Success success) {
				context.player().setItemInHand(context.hand(), success.heldItemTransformedTo());
				return success;
			}
			return result;
		});
	}
}
