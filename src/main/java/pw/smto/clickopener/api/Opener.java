package pw.smto.clickopener.api;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import pw.smto.clickopener.impl.ClickContext;
import pw.smto.clickopener.interfaces.OpenContextHolder;
import pw.smto.clickopener.interfaces.Openable;
import pw.smto.clickopener.interfaces.UseAllower;

@SuppressWarnings("unused")
public interface Opener<SELF extends Opener<SELF, T>, T extends OpenContext<T, SELF>> {
	T mutateContext(ClickContext context);

	default void preOpen(T context) {}

	InteractionResult open(T context);

	default void postOpen(T context) {
		final var handler = context.player().containerMenu;
		//Allow any ScreenHandlers that need to be forced
		if (handler instanceof UseAllower allower) allower.clickopener$allowUse();
		Openable.cast(context.getStack()).clickopener$setCloser(()->{
			if (context.player().containerMenu == handler) {
				context.player().closeContainer();
			}
		});
		((OpenContextHolder)handler).clickopener$setOpenContext(context);
	}

	default void onClose(T context) {
		Openable.cast(context.getStack()).clickopener$clearCloser();
	}

	default ItemStack getReplacingStack(T context) {
		return context.getStack();
	}
}
