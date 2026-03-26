package pw.smto.clickopener.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShulkerBoxBlock.class)
public abstract class ShulkerBoxBlockMixin {
	@Unique
	@SuppressWarnings("unused")
	//@Redirect(method = "getPickStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldView;getBlockEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntityType;)Ljava/util/Optional;"))
	public Optional<? extends BlockEntity> replaceGetBlockEntity(LevelReader world, BlockPos pos, BlockEntityType<?> type) {
		return Optional.ofNullable(world.getBlockEntity(pos)).filter(ShulkerBoxBlockEntity.class::isInstance);
	}
}
