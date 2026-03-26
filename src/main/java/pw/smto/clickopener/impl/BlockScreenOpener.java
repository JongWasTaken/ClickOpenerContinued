package pw.smto.clickopener.impl;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pw.smto.clickopener.api.Opener;
import java.util.Objects;

public interface BlockScreenOpener extends Opener<BlockScreenOpener, BlockOpenContext> {
	BlockScreenOpener DEFAULT_OPENER = new BlockScreenOpener() {
	};

	@Override
	default BlockOpenContext mutateContext(ClickContext clickContext) {
		if (!(clickContext.initialStack().getItem() instanceof BlockItem)) {
			throw new IllegalArgumentException("BlockItemScreenOpener cannot be used for non-block items.");
		}
		return new BlockOpenContext(clickContext, this);
	}

	@Override
	default InteractionResult open(BlockOpenContext context) {
		if (context.initialStack().getCount() != 1) return InteractionResult.FAIL;
		var result = context.getBlockState().useWithoutItem(context.world(), context.player(), context.hitResult());
		if (result.consumesAction()) {
			return result;
		}
		return context.runWithStackInHand(context::getCursorStack, context::setCursorStack, stack -> stack.useOn(context.toItemUsageContext()));
	}

	@Override
	default void onClose(BlockOpenContext context) {
		//Fake break the block to drop the items
		context.getBlockState().getBlock().affectNeighborsAfterRemoval(Blocks.AIR.defaultBlockState(), context.world(), context.pos(), false);
		Opener.super.onClose(context);
	}

	@Override
	default ItemStack getReplacingStack(BlockOpenContext context) {
		var stack = context.getBlockState().getBlock().getCloneItemStack(context.world(), context.pos(), context.getBlockState(), true);
		var ent = context.world().getBlockEntity(context.pos());
		if (ent != null) stack.applyComponents(ent.collectComponents()); // getPickStack does not do this anymore for some reason
		return stack;
	}

	default void onMarkDirty(BlockOpenContext context) {
		context.setStack(this.getReplacingStack(context));
	}

	default void onStateChange(BlockState oldState, BlockOpenContext context) {
		//State is unchanged -> do nothing
		if (oldState == context.getBlockState()) return;

		//Change to air -> destroy the item and close the screen
		if (context.getBlockState().isAir()) {
			context.getStack().setCount(0);
			return;
		}

		//Assumes other state changes don't close the screen
		context.setStack(this.getReplacingStack(context));
	}

	default BlockState getBlockState(BlockOpenContext context) {
		var block = Block.byItem(context.getStack().getItem());

		if (context.getStack().getComponents().has(DataComponents.BLOCK_STATE)) {
			return Objects.requireNonNull(context.getStack().get(DataComponents.BLOCK_STATE)).apply(block.defaultBlockState());
		}

		return block.defaultBlockState();
	}

	default BlockEntity getBlockEntity(BlockOpenContext context) {
		if (!(context.getBlockState().getBlock() instanceof EntityBlock provider)) return null;
		var blockEntity = provider.newBlockEntity(context.pos(), context.getBlockState());
		if (blockEntity != null) blockEntity.applyComponentsFromItemStack(context.getStack());
		return blockEntity;
	}
}
