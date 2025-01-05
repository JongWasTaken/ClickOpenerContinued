package pw.smto.clickopener.impl;

import pw.smto.clickopener.api.ClickType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ClickContext {
	private final ServerPlayerEntity player;
	private final Hand hand;
	private final Inventory clickedInventory;
	private final int slotIndex;
	private final ClickType clickType;
	private final ItemStack initialCursorStack;
	private final ItemStack initialStack;

	public ClickContext(ServerPlayerEntity player, Hand hand, Inventory clickedInventory, int slotIndex, ClickType clickType, ItemStack initialCursorStack, ItemStack initialStack) {
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

	public ServerPlayerEntity player() {
		return this.player;
	}

	public ServerWorld world() {
		return this.player().getServerWorld();
	}

	public BlockPos pos() {
		return this.player().getBlockPos();
	}

	public Hand hand() {
		return this.hand;
	}

	public Inventory clickedInventory() {
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
		return new BlockHitResult(this.pos().toCenterPos(), Direction.NORTH, this.pos(), true);
	}

	public ItemUsageContext toItemUsageContext() {
		return new ItemUsageContext(this.world(), this.player(), this.hand(), this.player().getStackInHand(this.hand()), this.hitResult());
	}
}
