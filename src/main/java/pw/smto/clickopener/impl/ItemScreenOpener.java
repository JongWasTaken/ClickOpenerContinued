package pw.smto.clickopener.impl;

import pw.smto.clickopener.api.Opener;
import net.minecraft.util.ActionResult;

public interface ItemScreenOpener extends Opener<ItemScreenOpener, ItemOpenContext> {
	ItemScreenOpener DEFAULT_OPENER = new ItemScreenOpener() {
	};

	@Override
	default ItemOpenContext mutateContext(ClickContext context) {
		return new ItemOpenContext(context, this);
	}

	@Override
	default ActionResult open(ItemOpenContext context) {
		return context.runWithStackInHand(context::getStack, context::setStack, stack -> {
			var result = stack.use(context.player().getEntityWorld(), context.player(), context.hand());
			if (result instanceof ActionResult.Success success) {
				context.player().setStackInHand(context.hand(), success.getNewHandStack());
				return success;
			}
			return result;
		});
	}
}
