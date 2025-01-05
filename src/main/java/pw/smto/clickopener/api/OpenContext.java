package pw.smto.clickopener.api;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import pw.smto.clickopener.impl.ClickContext;
import pw.smto.clickopener.interfaces.Openable;
import net.minecraft.item.ItemStack;

public abstract class OpenContext<SELF extends OpenContext<SELF, O>, O extends Opener<O, SELF>> extends ClickContext {
	private final O opener;
	private ItemStack cursorStack;
	private ItemStack stack;
	private boolean syncing;

	protected OpenContext(ClickContext context, O opener) {
		super(context);
		this.opener = opener;
		this.cursorStack = this.initialCursorStack();
		this.stack = this.initialStack();
		this.syncing = true;
	}

	public abstract SELF self();

	public O opener() {
		return this.opener;
	}

	public ItemStack getStack() {
		return this.stack;
	}

	public ItemStack getCursorStack() {
		return this.cursorStack;
	}
	
	public void setSyncing(boolean syncing) {
		this.syncing = syncing;
        this.sync();
	}
	
	public void sync() {
		if (!this.syncing) return;
        this.clickedInventory().setStack(this.slotIndex(), this.stack);
	}

	public void setStack(ItemStack stack) {
		Openable.cast(stack).clickopener$setCloser(Openable.cast(this.stack).clickopener$clearCloser());
		this.stack = stack;
        this.sync();
	}

	public void setCursorStack(ItemStack cursorStack) {
		this.cursorStack = cursorStack;
	}

	public <T> T runWithStackInHand(Supplier<ItemStack> stackSupplier, Consumer<ItemStack> stackReplacer, Function<ItemStack, T> action) {
		var actionStack = stackSupplier.get();
		var originalHandStack = this.player().getStackInHand(this.hand());
		if (actionStack == originalHandStack) return action.apply(originalHandStack);

        this.setSyncing(false);
        this.player().setStackInHand(this.hand(), actionStack);
		var result = action.apply(actionStack);
		stackReplacer.accept(this.player().getStackInHand(this.hand()));
        this.player().setStackInHand(this.hand(), originalHandStack);
        this.setSyncing(true);
		return result;
	}

	public void openerConsumer(BiConsumer<O, SELF> consumer) {
		consumer.accept(this.opener(), this.self());
	}

	public <R> R openerFunction(BiFunction<O, SELF, R> consumer) {
		return consumer.apply(this.opener(), this.self());
	}
}
