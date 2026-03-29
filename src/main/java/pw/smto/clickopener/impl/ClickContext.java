package pw.smto.clickopener.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import pw.smto.clickopener.api.ClickType;

public class ClickContext {
	private final ServerPlayer player;
	private final InteractionHand hand;
	private final Container clickedInventory;
	protected int slotIndex;
	private final ClickType clickType;
	private final ItemStack initialCursorStack;
	private final ItemStack initialStack;

	public ClickContext(ServerPlayer player, InteractionHand hand, Container clickedInventory, int slotIndex, ClickType clickType, ItemStack initialCursorStack, ItemStack initialStack) {
		this.player = player;
		this.hand = hand;
		this.clickedInventory = clickedInventory;
		this.slotIndex = slotIndex;
		this.clickType = clickType;
		this.initialCursorStack = initialCursorStack;
		this.initialStack = initialStack;
	}

	public ClickContext(ClickContext context) {
		this.player = context.player();
		this.hand = context.hand();
		this.clickedInventory = context.clickedInventory();
		this.slotIndex = context.slotIndex();
		this.clickType = context.clickType();
		this.initialCursorStack = context.initialCursorStack();
		this.initialStack = context.initialStack();
	}

	public ServerPlayer player() {
		return this.player;
	}

	public ServerLevel world() {
		return this.player().level();
	}

	public BlockPos pos() {
		return this.player().blockPosition();
	}

	public InteractionHand hand() {
		return this.hand;
	}

	public Container clickedInventory() {
		return this.clickedInventory;
	}

	public int slotIndex() {
		return this.slotIndex;
	}

	public ClickType clickType() {
		return this.clickType;
	}

	public ItemStack initialCursorStack() {
		return this.initialCursorStack;
	}

	public ItemStack initialStack() {
		return this.initialStack;
	}

	public BlockHitResult hitResult() {
		return new BlockHitResult(this.pos().getCenter(), Direction.NORTH, this.pos(), true);
	}

	public UseOnContext toItemUsageContext() {
		return new UseOnContext(this.world(), this.player(), this.hand(), this.player().getItemInHand(this.hand()), this.hitResult());
	}
}
