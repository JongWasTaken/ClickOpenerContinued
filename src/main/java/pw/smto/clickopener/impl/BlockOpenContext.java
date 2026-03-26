package pw.smto.clickopener.impl;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pw.smto.clickopener.api.OpenContext;
import pw.smto.clickopener.util.FakeWorld;

public class BlockOpenContext extends OpenContext<BlockOpenContext, BlockScreenOpener> {
	private final FakeWorld world;
	private BlockState blockState;
	private final BlockEntity blockEntity;

	public BlockOpenContext(ClickContext context, BlockScreenOpener opener) {
		super(context, opener);
		this.world = FakeWorld.create(this);
		this.blockState = opener.getBlockState(this);
		this.blockEntity = opener.getBlockEntity(this);
		if (this.blockEntity != null) this.blockEntity.setLevel(this.world());
	}

	@Override
	public FakeWorld world() {
		return this.world;
	}

	public BlockState getBlockState() {
		return this.blockState;
	}

	public BlockEntity getBlockEntity() {
		return this.blockEntity;
	}

	public void setBlockState(BlockState state) {
		var oldState = this.blockState;
		this.blockState = state;
        this.opener().onStateChange(oldState, this);
	}

	public boolean handles(BlockPos pos) {
		return Objects.equals(this.pos(), pos);
	}

	@Override
	public BlockOpenContext self() {
		return this;
	}
}

