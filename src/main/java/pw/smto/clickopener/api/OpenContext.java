package pw.smto.clickopener.api;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import pw.smto.clickopener.impl.ClickContext;
import pw.smto.clickopener.interfaces.Openable;

public abstract class OpenContext<SELF extends OpenContext<SELF, O>, O extends Opener<O, SELF>> extends ClickContext {
	private final O opener;
	private ItemStack cursorStack;
	private int lastStackHash;
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
		if (ItemStack.hashItemAndComponents(this.clickedInventory().getItem(this.slotIndex())) != this.lastStackHash) {
			// oh no, the item disappeared: search inventory for stack with matching hash
			for (int i = 0; i < this.clickedInventory().getContainerSize(); i++) {
				if (ItemStack.hashItemAndComponents(this.clickedInventory().getItem(i)) == this.lastStackHash) {
					this.slotIndex = i;
					this.clickedInventory().setItem(i, this.stack);
					return;
				}
			}
		} else this.clickedInventory().setItem(this.slotIndex(), this.stack);
	}

	public void setStack(ItemStack stack) {
		Openable.cast(stack).clickopener$setCloser(Openable.cast(this.stack).clickopener$clearCloser());
		this.lastStackHash = ItemStack.hashItemAndComponents(this.stack);
		this.stack = stack;
        this.sync();
	}

	public void setCursorStack(ItemStack cursorStack) {
		this.cursorStack = cursorStack;
	}

	public <T> T runWithStackInHand(Supplier<ItemStack> stackSupplier, Consumer<ItemStack> stackReplacer, Function<ItemStack, T> action) {
		var actionStack = stackSupplier.get();
		var originalHandStack = this.player().getItemInHand(this.hand());
		if (actionStack == originalHandStack) return action.apply(originalHandStack);

        this.setSyncing(false);
        this.player().setItemInHand(this.hand(), actionStack);
		var result = action.apply(actionStack);
		stackReplacer.accept(this.player().getItemInHand(this.hand()));
        this.player().setItemInHand(this.hand(), originalHandStack);
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
