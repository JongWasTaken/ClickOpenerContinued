package pw.smto.clickopener.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import com.mojang.datafixers.util.Pair;

import io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import pw.smto.clickopener.impl.BlockOpenContext;
import pw.smto.clickopener.impl.BlockScreenOpener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.TickPriority;

@SuppressWarnings({"deprecation", "java:S5803"})
public class FakeWorld extends ServerLevel {
	private static final long RANDOM_OFFSET = UnsafeAccess.fieldOffset(Level.class, "random");
	private static final long THREAD_SAFE_RANDOM_OFFSET = UnsafeAccess.fieldOffset(Level.class, "soundSeedGenerator");
	private static final long CONTEXT_OFFSET = UnsafeAccess.fieldOffset(FakeWorld.class, "context");

	@SuppressWarnings("unused")
    private final BlockOpenContext context;

	private FakeWorld() {
		super(null, null, null, null, null, null, false, 0, List.of(), false);
		throw new IllegalStateException("FakeWorld constructor should not be used.");
	}

	public static FakeWorld create(BlockOpenContext context) {
		try {
			var fakeWorld = (FakeWorld) UnsafeAccess.UNSAFE.allocateInstance(FakeWorld.class);
			//set public fields from world
            UnsafeAccess.UNSAFE.putObject(fakeWorld, FakeWorld.RANDOM_OFFSET, context.player().level().random);
			UnsafeAccess.UNSAFE.putObject(fakeWorld, FakeWorld.THREAD_SAFE_RANDOM_OFFSET, context.player().level().soundSeedGenerator);
			//isClient is false by default
			UnsafeAccess.UNSAFE.putObject(fakeWorld, FakeWorld.CONTEXT_OFFSET, context);
			return fakeWorld;
		} catch (InstantiationException e) {
			throw new ItemOpenException("Failed to allocate instance of FakeWorld.", e);
		}
	}

	private ServerLevel delegate() {
		return this.context.player().level();
	}

	private <T> T ifHandlesOrElse(BlockPos pos, Supplier<T> ifHandles, Supplier<T> orElse) {
		return this.context.handles(pos) ? ifHandles.get() : orElse.get();
	}

	private void ifHandlesOrElse(BlockPos pos, Runnable ifHandles, Runnable orElse) {
		if (this.context.handles(pos)) {
			ifHandles.run();
		} else {
			orElse.run();
		}
	}

	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(state);
			return true;
		}, () -> this.delegate().setBlock(pos, state, flags));
	}

	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(state);
			return true;
		}, () -> this.delegate().setBlock(pos, state, flags, maxUpdateDepth));
	}

	@Override
	public boolean setBlockAndUpdate(BlockPos pos, BlockState state) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(state);
			return true;
		}, () -> this.delegate().setBlockAndUpdate(pos, state));
	}

	@Override
	public boolean removeBlock(BlockPos pos, boolean move) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(Blocks.AIR.defaultBlockState());
			return true;
		}, () -> this.delegate().removeBlock(pos, move));
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return this.ifHandlesOrElse(pos, this.context::getBlockState, () -> this.delegate().getBlockState(pos));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
		return this.ifHandlesOrElse(pos, () -> (Optional<T>) Optional.ofNullable(this.context.getBlockEntity()).filter(be -> be.getType() == type), () -> this.delegate().getBlockEntity(pos, type));
	}

	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return this.ifHandlesOrElse(pos, this.context::getBlockEntity, () -> this.delegate().getBlockEntity(pos));
	}

	@Override
	public void blockEvent(BlockPos pos, Block block, int type, int data) {
        this.ifHandlesOrElse(pos, () -> this.context.getBlockState().triggerEvent(this, pos, type, data), () -> this.delegate().blockEvent(pos, block, type, data));
	}

	@Override
	protected LevelEntityGetter<Entity> getEntities() {
		return null;
	}

	/*
	@Override
	public void replaceWithStateForNeighborUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
		if (context.handles(pos)) return;
		delegate().replaceWithStateForNeighborUpdate(direction, neighborState, pos, neighborPos, flags, maxUpdateDepth);
	}

	 */

	@Override
	public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {
		if (this.context.handles(pos)) return;
        this.delegate().updateNeighbourForOutputSignal(pos, block);
	}

	@Override
	public void blockEntityChanged(BlockPos pos) {
        this.ifHandlesOrElse(pos, () -> this.context.openerConsumer(BlockScreenOpener::onMarkDirty), () -> this.delegate().blockEntityChanged(pos));
	}

	@Override
	public boolean noCollision(AABB box) {
		return true;
	}

	/*
	 * Delegated methods
	 */

	@Override
	public boolean ensureCanWrite(BlockPos pos) {
		return this.delegate().ensureCanWrite(pos);
	}

	@Override
	public void addFreshEntityWithPassengers(Entity entity) {
        this.delegate().addFreshEntityWithPassengers(entity);
	}

	@Override
	public int getDirectSignal(BlockPos pos, Direction direction) {
		return this.delegate().getDirectSignal(pos, direction);
	}

	@Override
	public void setCurrentlyGenerating(Supplier<String> structureName) {
        this.delegate().setCurrentlyGenerating(structureName);
	}

	@Override
	public int getBrightness(LightLayer type, BlockPos pos) {
		return this.delegate().getBrightness(type, pos);
	}

	@Override
	public int getDirectSignalTo(BlockPos pos) {
		return this.delegate().getDirectSignalTo(pos);
	}

	@Override
	public float getMoonBrightness(BlockPos pos) {
		return this.delegate().getMoonBrightness(pos);
	}

	@Override
	public int getRawBrightness(BlockPos pos, int ambientDarkness) {
		return this.delegate().getRawBrightness(pos, ambientDarkness);
	}

	@Override
	public boolean canSeeSky(BlockPos pos) {
		return this.delegate().canSeeSky(pos);
	}

	@Override
	public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) {
		return this.delegate().getEntityCollisions(entity, box);
	}

	@Override
	public int getSectionsCount() {
		return this.delegate().getSectionsCount();
	}

	@Override
	public boolean isUnobstructed(Entity except, VoxelShape shape) {
		return this.delegate().isUnobstructed(except, shape);
	}

	@Override
	public boolean isUnobstructed(BlockState state, BlockPos pos, CollisionContext context) {
		return this.delegate().isUnobstructed(state, pos, context);
	}

	@Override
	public BlockPos getHeightmapPos(Heightmap.Types heightmap, BlockPos pos) {
		return this.delegate().getHeightmapPos(heightmap, pos);
	}

	@Override
	public EnvironmentAttributeSystem environmentAttributes() {
		return this.delegate().environmentAttributes();
	}

	@Override
	public @Nullable Object getBlockEntityRenderData(BlockPos pos) {
		return this.delegate().getBlockEntityRenderData(pos);
	}

	@Override
	public int getMinSectionY() {
		return this.delegate().getMinSectionY();
	}

	@Override
	public int getControlInputSignal(BlockPos pos, Direction direction, boolean onlyFromGate) {
		return this.delegate().getControlInputSignal(pos, direction, onlyFromGate);
	}

	@Override
	public <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB box,
			Predicate<? super T> predicate) {
		return this.delegate().getEntitiesOfClass(entityClass, box, predicate);
	}

	@Override
	public boolean isUnobstructed(Entity entity) {
		return this.delegate().isUnobstructed(entity);
	}

	@Override
	public boolean noCollision(Entity entity) {
		return this.delegate().noCollision(entity);
	}

	@Override
	public void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        this.delegate().scheduleTick(pos, block, delay, priority);
	}

	@Override
	public Holder<Biome> getBiome(BlockPos pos) {
		return this.delegate().getBiome(pos);
	}

	@Override
	public int getMaxSectionY() {
		return this.delegate().getMaxSectionY();
	}

	@Override
	public boolean noCollision(Entity entity, AABB box) {
		return this.delegate().noCollision(entity, box);
	}

	@Override
	public Stream<BlockState> getBlockStatesIfLoaded(AABB box) {
		return this.delegate().getBlockStatesIfLoaded(box);
	}

	@Override
	public void scheduleTick(BlockPos pos, Block block, int delay) {
        this.delegate().scheduleTick(pos, block, delay);
	}

	@Override
	public int getLightEmission(BlockPos pos) {
		return this.delegate().getLightEmission(pos);
	}

	@Override
	public boolean hasSignal(BlockPos pos, Direction direction) {
		return this.delegate().hasSignal(pos, direction);
	}

	@Override
	public List<Entity> getEntities(Entity except, AABB box) {
		return this.delegate().getEntities(except, box);
	}

	@Override
	public Stream<BlockState> getBlockStates(AABB box) {
		return this.delegate().getBlockStates(box);
	}

	@Override
	public void scheduleTick(BlockPos pos, Fluid fluid, int delay, TickPriority priority) {
        this.delegate().scheduleTick(pos, fluid, delay, priority);
	}

	@Override
	public int getSignal(BlockPos pos, Direction direction) {
		return this.delegate().getSignal(pos, direction);
	}

	@Override
	public BlockHitResult isBlockInLine(ClipBlockStateContext context) {
		return this.delegate().isBlockInLine(context);
	}

	@Override
	public boolean isOutsideBuildHeight(BlockPos pos) {
		return this.delegate().isOutsideBuildHeight(pos);
	}

	@Override
	public int hashCode() {
		return this.delegate().hashCode();
	}

	@Override
	public int getClientLeafTintColor(BlockPos pos) {
		return this.delegate().getClientLeafTintColor(pos);
	}

	@Override
	public void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
        this.delegate().scheduleTick(pos, fluid, delay);
	}

	@Override
	public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
		return this.delegate().getNoiseBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public Iterable<VoxelShape> getCollisions(Entity entity, AABB box) {
		return this.delegate().getCollisions(entity, box);
	}

	@Override
	public boolean hasNeighborSignal(BlockPos pos) {
		return this.delegate().hasNeighborSignal(pos);
	}

	@Override
	public boolean isOutsideBuildHeight(int y) {
		return this.delegate().isOutsideBuildHeight(y);
	}

	@Override
	public Difficulty getDifficulty() {
		return this.delegate().getDifficulty();
	}

	@Override
	public Iterable<VoxelShape> getBlockCollisions(Entity entity, AABB box) {
		return this.delegate().getBlockCollisions(entity, box);
	}

	@Override
	public boolean hasChunk(int chunkX, int chunkZ) {
		return this.delegate().hasChunk(chunkX, chunkZ);
	}

	@Override
	public BlockHitResult clip(ClipContext context) {
		return this.delegate().clip(context);
	}

	@Override
	public boolean hasBiomes() {
		return this.delegate().hasBiomes();
	}

	@Override
	public int getSectionIndex(int y) {
		return this.delegate().getSectionIndex(y);
	}

	@Override
	public int getBestNeighborSignal(BlockPos pos) {
		return this.delegate().getBestNeighborSignal(pos);
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean drop) {
		return this.delegate().destroyBlock(pos, drop);
	}

	@Override
	public boolean collidesWithSuffocatingBlock(Entity entity, AABB box) {
		return this.delegate().collidesWithSuffocatingBlock(entity, box);
	}

	@Override
	public int getMinY() {
		return this.delegate().getMinY();
	}

	@Override
	public int getHeight() {
		return this.delegate().getHeight();
	}

	@Override
	public int getSectionIndexFromSectionY(int coord) {
		return this.delegate().getSectionIndexFromSectionY(coord);
	}

	@Override
	public @UnknownNullability Holder<Biome> getBiomeFabric(BlockPos pos) {
		return this.delegate().getBiomeFabric(pos);
	}

	@Override
	public <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB box) {
		return this.delegate().getEntitiesOfClass(entityClass, box);
	}

	@Override
	public Optional<BlockPos> findSupportingBlock(Entity entity, AABB box) {
		return this.delegate().findSupportingBlock(entity, box);
	}

	@Override
	public int getSectionYFromSectionIndex(int index) {
		return this.delegate().getSectionYFromSectionIndex(index);
	}

	@Override
	public boolean isEmptyBlock(BlockPos pos) {
		return this.delegate().isEmptyBlock(pos);
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean drop, Entity breakingEntity) {
		return this.delegate().destroyBlock(pos, drop, breakingEntity);
	}

	@Override
	public boolean canSeeSkyFromBelowWater(BlockPos pos) {
		return this.delegate().canSeeSkyFromBelowWater(pos);
	}

	@Override
	public void levelEvent(int eventId, BlockPos pos, int data) {
        this.delegate().levelEvent(eventId, pos, data);
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
	public boolean equals(Object obj) {
		return this.delegate().equals(obj);
	}

	@Override
	public void gameEvent(Entity entity, Holder<GameEvent> event, Vec3 pos) {
        this.delegate().gameEvent(entity, event, pos);
	}

	@Override
	public void gameEvent(Entity entity, Holder<GameEvent> event, BlockPos pos) {
        this.delegate().gameEvent(entity, event, pos);
	}

	@Override
	public Optional<Vec3> findFreePosition(Entity entity, VoxelShape shape, Vec3 target, double x, double y,
			double z) {
		return this.delegate().findFreePosition(entity, shape, target, x, y, z);
	}

	@Override
	public Player getNearestPlayer(double x, double y, double z, double maxDistance,
			Predicate<Entity> targetPredicate) {
		return this.delegate().getNearestPlayer(x, y, z, maxDistance, targetPredicate);
	}

	@Override
	public BlockHitResult clipWithInteractionOverride(Vec3 start, Vec3 end, BlockPos pos, VoxelShape shape, BlockState state) {
		return this.delegate().clipWithInteractionOverride(start, end, pos, shape, state);
	}

	@Override
	public void gameEvent(Holder<GameEvent> event, BlockPos pos, GameEvent.Context emitter) {
        this.delegate().gameEvent(event, pos, emitter);
	}

	@Override
	public float getPathfindingCostFromLightLevels(BlockPos pos) {
		return this.delegate().getPathfindingCostFromLightLevels(pos);
	}

	@Override
	public float getLightLevelDependentMagicValue(BlockPos pos) {
		return this.delegate().getLightLevelDependentMagicValue(pos);
	}

	@Override
	public double getBlockFloorHeight(VoxelShape blockCollisionShape,
			Supplier<VoxelShape> belowBlockCollisionShapeGetter) {
		return this.delegate().getBlockFloorHeight(blockCollisionShape, belowBlockCollisionShapeGetter);
	}

	@Override
	public Player getNearestPlayer(Entity entity, double maxDistance) {
		return this.delegate().getNearestPlayer(entity, maxDistance);
	}

	@Override
	public Player getNearestPlayer(double x, double y, double z, double maxDistance, boolean ignoreCreative) {
		return this.delegate().getNearestPlayer(x, y, z, maxDistance, ignoreCreative);
	}

	@Override
	public ChunkAccess getChunk(BlockPos pos) {
		return this.delegate().getChunk(pos);
	}

	@Override
	public double getBlockFloorHeight(BlockPos pos) {
		return this.delegate().getBlockFloorHeight(pos);
	}

	@Override
	public boolean hasNearbyAlivePlayer(double x, double y, double z, double range) {
		return this.delegate().hasNearbyAlivePlayer(x, y, z, range);
	}

	@Override
	public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status) {
		return this.delegate().getChunk(chunkX, chunkZ, status);
	}

	@Override
	public boolean isWaterAt(BlockPos pos) {
		return this.delegate().isWaterAt(pos);
	}

	@Override
	public Player getNearestPlayer(TargetingConditions targetPredicate, LivingEntity entity) {
		return this.delegate().getNearestPlayer(targetPredicate, entity);
	}

	@Override
	public boolean containsAnyLiquid(AABB box) {
		return this.delegate().containsAnyLiquid(box);
	}

	@Override
	public Player getNearestPlayer(TargetingConditions targetPredicate, LivingEntity entity, double x, double y,
			double z) {
		return this.delegate().getNearestPlayer(targetPredicate, entity, x, y, z);
	}

	@Override
	public Player getNearestPlayer(TargetingConditions targetPredicate, double x, double y, double z) {
		return this.delegate().getNearestPlayer(targetPredicate, x, y, z);
	}

	@Override
	public boolean isClientSide() {
		return this.delegate().isClientSide();
	}

	@Override
	public <T extends LivingEntity> T getNearestEntity(Class<? extends T> entityClass, TargetingConditions targetPredicate,
			LivingEntity entity, double x, double y, double z, AABB box) {
		return this.delegate().getNearestEntity(entityClass, targetPredicate, entity, x, y, z, box);
	}

	@Override
	public boolean isInWorldBounds(BlockPos pos) {
		return this.delegate().isInWorldBounds(pos);
	}

	@Override
	public int getMaxLocalRawBrightness(BlockPos pos) {
		return this.delegate().getMaxLocalRawBrightness(pos);
	}

	@Override
	public int getMaxLocalRawBrightness(BlockPos pos, int ambientDarkness) {
		return this.delegate().getMaxLocalRawBrightness(pos, ambientDarkness);
	}

	@Override
	public <T extends LivingEntity> T getNearestEntity(List<? extends T> entityList, TargetingConditions targetPredicate,
			LivingEntity entity, double x, double y, double z) {
		return this.delegate().getNearestEntity(entityList, targetPredicate, entity, x, y, z);
	}

	@Override
	public boolean hasChunkAt(int x, int z) {
		return this.delegate().hasChunkAt(x, z);
	}

	@Override
	public boolean hasChunkAt(BlockPos pos) {
		return this.delegate().hasChunkAt(pos);
	}

	@Override
	public boolean hasChunksAt(BlockPos min, BlockPos max) {
		return this.delegate().hasChunksAt(min, max);
	}

	@Override
	public List<Player> getNearbyPlayers(TargetingConditions targetPredicate, LivingEntity entity, AABB box) {
		return this.delegate().getNearbyPlayers(targetPredicate, entity, box);
	}

	@Override
	public boolean hasChunksAt(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		return this.delegate().hasChunksAt(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public <T extends LivingEntity> List<T> getNearbyEntities(Class<T> entityClass, TargetingConditions targetPredicate,
			LivingEntity targetingEntity, AABB box) {
		return this.delegate().getNearbyEntities(entityClass, targetPredicate, targetingEntity, box);
	}

	@Override
	public boolean hasChunksAt(int minX, int minZ, int maxX, int maxZ) {
		return this.delegate().hasChunksAt(minX, minZ, maxX, maxZ);
	}

	@Override
	public Player getPlayerByUUID(UUID uuid) {
		return this.delegate().getPlayerByUUID(uuid);
	}

	@Override
	public LevelChunk getChunkAt(BlockPos pos) {
		return this.delegate().getChunkAt(pos);
	}

	@Override
	public <T> HolderLookup<T> holderLookup(
			ResourceKey<? extends Registry<? extends T>> registryRef) {
		return this.delegate().holderLookup(registryRef);
	}

	@Override
	public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
		return this.delegate().getChunk(chunkX, chunkZ, leastStatus, create);
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean drop, Entity breakingEntity, int maxUpdateDepth) {
		return this.delegate().destroyBlock(pos, drop, breakingEntity, maxUpdateDepth);
	}

	@Override
	public void setDragonFight(@org.jspecify.annotations.Nullable EnderDragonFight fight) {
		this.delegate().setDragonFight(fight);
	}

	@Override
	public void addDestroyBlockEffect(BlockPos pos, BlockState state) {
        this.delegate().addDestroyBlockEffect(pos, state);
	}

	@Override
	public void setBlocksDirty(BlockPos pos, BlockState old, BlockState updated) {
        this.delegate().setBlocksDirty(pos, old, updated);
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) {
		return this.delegate().getUncachedNoiseBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public StructureManager structureManager() {
		return this.delegate().structureManager();
	}

	@Override
	public void tick(BooleanSupplier shouldKeepTicking) {
        this.delegate().tick(shouldKeepTicking);
	}

	@Override
	public int getHeight(Heightmap.Types heightmap, int x, int z) {
		return this.delegate().getHeight(heightmap, x, z);
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return this.delegate().getLightEngine();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return this.delegate().getFluidState(pos);
	}

	@Override
	public boolean isBrightOutside() {
		return this.delegate().isBrightOutside();
	}

	@Override
	public boolean isDarkOutside() {
		return this.delegate().isDarkOutside();
	}

	@Override
	public void playSound(Entity except, BlockPos pos, SoundEvent sound, SoundSource category, float volume,
			float pitch) {
        this.delegate().playSound(except, pos, sound, category, volume, pitch);
	}

	@Override
	public boolean shouldTickBlocksAt(long chunkPos) {
		return this.delegate().shouldTickBlocksAt(chunkPos);
	}

	@Override
	public void tickCustomSpawners(boolean spawnMonsters) {
		this.delegate().tickCustomSpawners(spawnMonsters);
	}

	@Override
	public void addBlockEntityTicker(TickingBlockEntity ticker) {
        this.delegate().addBlockEntityTicker(ticker);
	}

	@Override
	public void tickChunk(LevelChunk chunk, int randomTickSpeed) {
        this.delegate().tickChunk(chunk, randomTickSpeed);
	}

	@Override
	public <T extends Entity> void guardEntityTick(Consumer<T> tickConsumer, T entity) {
        this.delegate().guardEntityTick(tickConsumer, entity);
	}

	@Override
	public boolean shouldTickDeath(Entity entity) {
		return this.delegate().shouldTickDeath(entity);
	}

	@Override
	public boolean shouldTickBlocksAt(BlockPos pos) {
		return this.delegate().shouldTickBlocksAt(pos);
	}

	/*
	@Override
	public Explosion createExplosion(Entity entity, double x, double y, double z, float power,
			ExplosionSourceType explosionSourceType) {
		return delegate().createExplosion(entity, x, y, z, power, explosionSourceType);
	}

	@Override
	public Explosion createExplosion(Entity entity, double x, double y, double z, float power, boolean createFire,
			ExplosionSourceType explosionSourceType) {
		return delegate().createExplosion(entity, x, y, z, power, createFire, explosionSourceType);
	}

	@Override
	public Explosion createExplosion(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, Vec3d pos,
			float power, boolean createFire, ExplosionSourceType explosionSourceType) {
		return delegate().createExplosion(entity, damageSource, behavior, pos, power, createFire, explosionSourceType);
	}

	@Override
	public Explosion createExplosion(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, double x,
			double y, double z, float power, boolean createFire, ExplosionSourceType explosionSourceType,
			boolean particles) {
		return delegate().createExplosion(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType);
	}

	 */

	@Override
	public void setBlockEntity(BlockEntity blockEntity) {
        this.delegate().setBlockEntity(blockEntity);
	}

	@Override
	public boolean isHandlingTick() {
		return this.delegate().isHandlingTick();
	}

	@Override
	public boolean canSleepThroughNights() {
		return this.delegate().canSleepThroughNights();
	}

	@Override
	public void removeBlockEntity(BlockPos pos) {
        this.delegate().removeBlockEntity(pos);
	}

	@Override
	public boolean loadedAndEntityCanStandOnFace(BlockPos pos, Entity entity, Direction direction) {
		return this.delegate().loadedAndEntityCanStandOnFace(pos, entity, direction);
	}

	@Override
	public void updateSleepingPlayerList() {
        this.delegate().updateSleepingPlayerList();
	}

	@Override
	public boolean loadedAndEntityCanStandOn(BlockPos pos, Entity entity) {
		return this.delegate().loadedAndEntityCanStandOn(pos, entity);
	}

	@Override
	public void updateSkyBrightness() {
        this.delegate().updateSkyBrightness();
	}

	/*
	@Override
	public void setMobSpawnOptions(boolean spawnMonsters, boolean spawnAnimals) {
		delegate().setMobSpawnOptions(spawnMonsters, spawnAnimals);
	}

	 */

	@Override
	public LevelData.RespawnData getRespawnData() {
		return this.delegate().getRespawnData();
	}

	@Override
	public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
		return this.delegate().getChunkForCollisions(chunkX, chunkZ);
	}

	@Override
	public List<Entity> getEntities(Entity except, AABB box, Predicate<? super Entity> predicate) {
		return this.delegate().getEntities(except, box, predicate);
	}

	@Override
	public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> filter, AABB box,
			Predicate<? super T> predicate) {
		return this.delegate().getEntities(filter, box, predicate);
	}

	@Override
	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, AABB box,
			Predicate<? super T> predicate, List<? super T> result) {
        this.delegate().getEntities(filter, box, predicate, result);
	}

	@Override
	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, AABB box,
			Predicate<? super T> predicate, List<? super T> result, int limit) {
        this.delegate().getEntities(filter, box, predicate, result, limit);
	}

	@Override
	public void resetEmptyTime() {
        this.delegate().resetEmptyTime();
	}

	@Override
	public void tickNonPassenger(Entity entity) {
        this.delegate().tickNonPassenger(entity);
	}

	@Override
	public int getSeaLevel() {
		return this.delegate().getSeaLevel();
	}

	@Override
	public long getGameTime() {
		return this.delegate().getGameTime();
	}


	@Override
	public boolean mayInteract(Entity entity, BlockPos pos) {
		return this.delegate().mayInteract(entity, pos);
	}

	@Override
	public void save(ProgressListener progressListener, boolean flush, boolean savingDisabled) {
        this.delegate().save(progressListener, flush, savingDisabled);
	}

	@Override
	public LevelData getLevelData() {
		return this.delegate().getLevelData();
	}

	@Override
	public GameRules getGameRules() {
		return this.delegate().getGameRules();
	}

	@Override
	public float getThunderLevel(float delta) {
		return this.delegate().getThunderLevel(delta);
	}

	@Override
	public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> filter,
			Predicate<? super T> predicate) {
		return this.delegate().getEntities(filter, predicate);
	}

	@Override
	public void setThunderLevel(float thunderGradient) {
        this.delegate().setThunderLevel(thunderGradient);
	}

	@Override
	public float getRainLevel(float delta) {
		return this.delegate().getRainLevel(delta);
	}

	@Override
	public void setRainLevel(float rainGradient) {
        this.delegate().setRainLevel(rainGradient);
	}

	@Override
	public boolean isThundering() {
		return this.delegate().isThundering();
	}

	@Override
	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate,
			List<? super T> result) {
        this.delegate().getEntities(filter, predicate, result);
	}

	@Override
	public boolean isRaining() {
		return this.delegate().isRaining();
	}

	@Override
	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, Predicate<? super T> predicate,
			List<? super T> result, int limit) {
        this.delegate().getEntities(filter, predicate, result, limit);
	}

	@Override
	public boolean isRainingAt(BlockPos pos) {
		return this.delegate().isRainingAt(pos);
	}

	@Override
	public List<? extends EnderDragon> getDragons() {
		return this.delegate().getDragons();
	}

	@Override
	public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
		return this.delegate().getPlayers(predicate);
	}

	@Override
	public CrashReportCategory fillReportDetails(CrashReport report) {
		return this.delegate().fillReportDetails(report);
	}

	@Override
	public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate, int limit) {
		return this.delegate().getPlayers(predicate, limit);
	}

	@Override
	public ServerPlayer getRandomPlayer() {
		return this.delegate().getRandomPlayer();
	}

	//@Override
	//public void addFireworkParticle(double x, double y, double z, double velocityX, double velocityY, double velocityZ,
	//		NbtCompound nbt) {
	//	delegate().addFireworkParticle(x, y, z, velocityX, velocityY, velocityZ, nbt);
	//}

	@Override
	public boolean addFreshEntity(Entity entity) {
		return this.delegate().addFreshEntity(entity);
	}

	@Override
	public boolean addWithUUID(Entity entity) {
		return this.delegate().addWithUUID(entity);
	}

	@Override
	public void addDuringTeleport(Entity entity) {
        this.delegate().addDuringTeleport(entity);
	}

	@Override
	public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
		return this.delegate().getCurrentDifficultyAt(pos);
	}

	//@Override
	//public void onPlayerTeleport(ServerPlayerEntity player) {
	//	delegate().onPlayerTeleport(player);
	//}

	@Override
	public int getSkyDarken() {
		return this.delegate().getSkyDarken();
	}

	//@Override
	//public void onPlayerChangeDimension(ServerPlayerEntity player) {
	//	delegate().onPlayerChangeDimension(player);
	//}

	@Override
	public void setSkyFlashTime(int lightningTicksLeft) {
        this.delegate().setSkyFlashTime(lightningTicksLeft);
	}

	@Override
	public WorldBorder getWorldBorder() {
		return this.delegate().getWorldBorder();
	}

	@Override
	public void sendPacketToServer(Packet<?> packet) {
        this.delegate().sendPacketToServer(packet);
	}

	@Override
	public void addNewPlayer(ServerPlayer player) {
        this.delegate().addNewPlayer(player);
	}

	@Override
	public DimensionType dimensionType() {
		return this.delegate().dimensionType();
	}

	//@Override
	//public RegistryKey<DimensionType> getDimensionKey() {
	//	return delegate().getDimensionKey();
	//}

	@Override
	public void addRespawnedPlayer(ServerPlayer player) {
        this.delegate().addRespawnedPlayer(player);
	}

	@Override
	public Holder<DimensionType> dimensionTypeRegistration() {
		return this.delegate().dimensionTypeRegistration();
	}

	@Override
	public ResourceKey<Level> dimension() {
		return this.delegate().dimension();
	}

	@Override
	public RandomSource getRandom() {
		return this.delegate().getRandom();
	}

	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
		return this.delegate().isStateAtPosition(pos, state);
	}

	@Override
	public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) {
		return this.delegate().isFluidAtPosition(pos, state);
	}

	@Override
	public BlockPos getBlockRandomPos(int x, int y, int z, int i) {
		return this.delegate().getBlockRandomPos(x, y, z, i);
	}

	@Override
	public boolean tryAddFreshEntityWithPassengers(Entity entity) {
		return this.delegate().tryAddFreshEntityWithPassengers(entity);
	}

	/*

	@Override
	public Profiler getProfiler() {
		return delegate().getProfiler();
	}

	@Override
	public Supplier<Profiler> getProfilerSupplier() {
		return delegate().getProfilerSupplier();
	}

	 */

	@Override
	public BiomeManager getBiomeManager() {
		return this.delegate().getBiomeManager();
	}

	@Override
	public void unload(LevelChunk chunk) {
        this.delegate().unload(chunk);
	}

	@Override
	public void removePlayerImmediately(ServerPlayer player, Entity.RemovalReason reason) {
        this.delegate().removePlayerImmediately(player, reason);
	}

	@Override
	public long nextSubTickCount() {
		return this.delegate().nextSubTickCount();
	}

	@Override
	public void destroyBlockProgress(int entityId, BlockPos pos, int progress) {
        this.delegate().destroyBlockProgress(entityId, pos, progress);
	}

	@Override
	public RegistryAccess registryAccess() {
		return this.delegate().registryAccess();
	}

	@Override
	public DamageSources damageSources() {
		return this.delegate().damageSources();
	}

	@Override
	public LevelChunk getChunk(int chunkX, int chunkZ) {
		return this.delegate().getChunk(chunkX, chunkZ);
	}

	@Override
	public void playSeededSound(@Nullable Entity source, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.delegate().playSeededSound(source, x, y, z, sound, category, volume, pitch, seed);
	}

	@Override
	public void playSeededSound(@Nullable Entity source, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed) {
        this.delegate().playSeededSound(source, entity, sound, category, volume, pitch, seed);
	}

	@Override
	public void globalLevelEvent(int eventId, BlockPos pos, int data) {
        this.delegate().globalLevelEvent(eventId, pos, data);
	}

	@Override
	public void levelEvent(@Nullable Entity source, int eventId, BlockPos pos, int data) {
        this.delegate().levelEvent(source, eventId, pos, data);
	}

	@Override
	public int getLogicalHeight() {
		return this.delegate().getLogicalHeight();
	}

	@Override
	public void gameEvent(Holder<GameEvent> event, Vec3 emitterPos, GameEvent.Context emitter) {
        this.delegate().gameEvent(event, emitterPos, emitter);
	}

	@Override
	public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        this.delegate().sendBlockUpdated(pos, oldState, newState, flags);
	}

	@Override
	public void updateNeighborsAt(BlockPos pos, Block sourceBlock, @Nullable Orientation orientation) {
        this.delegate().updateNeighborsAt(pos, sourceBlock, orientation);
	}

	@Override
	public void broadcastEntityEvent(Entity entity, byte status) {
        this.delegate().broadcastEntityEvent(entity, status);
	}

	@Override
	public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
        this.delegate().broadcastDamageEvent(entity, damageSource);
	}

	@Override
	public @NotNull MinecraftServer getServer() {
		return this.delegate().getServer();
	}

	@Override
	public PortalForcer getPortalForcer() {
		return this.delegate().getPortalForcer();
	}

	@Override
	public StructureTemplateManager getStructureManager() {
		return this.delegate().getStructureManager();
	}

	@Override
	public <T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count,
			double deltaX, double deltaY, double deltaZ, double speed) {
		return this.delegate().sendParticles(particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}

	@Override
	public <T extends ParticleOptions> boolean sendParticles(
			ServerPlayer viewer,
			T parameters,
			boolean force,
			boolean important,
			double x,
			double y,
			double z,
			int count,
			double offsetX,
			double offsetY,
			double offsetZ,
			double speed
	) {
		return this.delegate().sendParticles(viewer, parameters, force, important, x, y, z, count, offsetX, offsetY, offsetZ, speed);
	}

	@Override
	public Entity getEntity(int id) {
		return this.delegate().getEntity(id);
	}

	@Override
	public Entity getEntity(UUID uuid) {
		return this.delegate().getEntity(uuid);
	}

	@Override
	public BlockPos findNearestMapStructure(TagKey<Structure> structureTag, BlockPos pos, int radius,
			boolean skipReferencedStructures) {
		return this.delegate().findNearestMapStructure(structureTag, pos, radius, skipReferencedStructures);
	}

	@Override
	public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(Predicate<Holder<Biome>> predicate, BlockPos pos,
			int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval) {
		return this.delegate().findClosestBiome3d(predicate, pos, radius, horizontalBlockCheckInterval, verticalBlockCheckInterval);
	}

	@Override
	public RecipeManager recipeAccess() {
		return this.delegate().recipeAccess();
	}

	@Override
	public boolean noSave() {
		return this.delegate().noSave();
	}

	@Override
	public MapItemSavedData getMapData(MapId id) {
		return this.delegate().getMapData(id);
	}

	@Override
	public void setMapData(MapId id, MapItemSavedData state) {
        this.delegate().setMapData(id, state);
	}

	@Override
	public MapId getFreeMapId() {
		return this.delegate().getFreeMapId();
	}

	@Override
	public void setRespawnData(LevelData.RespawnData spawnPoint) {
		this.delegate().setRespawnData(spawnPoint);
	}

	@Override
	public LongSet getForceLoadedChunks() {
		return this.delegate().getForceLoadedChunks();
	}

	@Override
	public boolean setChunkForced(int x, int z, boolean forced) {
		return this.delegate().setChunkForced(x, z, forced);
	}

	@Override
	public List<ServerPlayer> players() {
		return this.delegate().players();
	}

	@Override
	public PoiManager getPoiManager() {
		return this.delegate().getPoiManager();
	}

	@Override
	public boolean isVillage(BlockPos pos) {
		return this.delegate().isVillage(pos);
	}

	@Override
	public boolean isVillage(SectionPos sectionPos) {
		return this.delegate().isVillage(sectionPos);
	}

	@Override
	public boolean isCloseToVillage(BlockPos pos, int maxDistance) {
		return this.delegate().isCloseToVillage(pos, maxDistance);
	}

	@Override
	public int sectionsToVillage(SectionPos pos) {
		return this.delegate().sectionsToVillage(pos);
	}

	@Override
	public Raids getRaids() {
		return this.delegate().getRaids();
	}

	@Override
	public Raid getRaidAt(BlockPos pos) {
		return this.delegate().getRaidAt(pos);
	}

	@Override
	public boolean isRaided(BlockPos pos) {
		return this.delegate().isRaided(pos);
	}

	@Override
	public void onReputationEvent(ReputationEventType interaction, Entity entity, ReputationEventHandler observer) {
        this.delegate().onReputationEvent(interaction, entity, observer);
	}

	@Override
	public void saveDebugReport(Path path) throws IOException {
        this.delegate().saveDebugReport(path);
	}

	@Override
	public void clearBlockEvents(BoundingBox box) {
        this.delegate().clearBlockEvents(box);
	}

	@Override
	public void updateNeighborsAt(BlockPos pos, Block block) {
        this.delegate().updateNeighborsAt(pos, block);
	}

	@Override
	public Iterable<Entity> getAllEntities() {
		return this.delegate().getAllEntities();
	}

	@Override
	public String toString() {
		return this.delegate().toString();
	}

	@Override
	public boolean isFlat() {
		return this.delegate().isFlat();
	}

	@Override
	public long getSeed() {
		return this.delegate().getSeed();
	}

	@Override
	public ServerLevel getLevel() {
		return this.delegate().getLevel();
	}

	@Override
	public String getWatchdogStats() {
		return this.delegate().getWatchdogStats();
	}

	@Override
	public void addLegacyChunkEntities(Stream<Entity> entities) {
        this.delegate().addLegacyChunkEntities(entities);
	}

	@Override
	public void addWorldGenChunkEntities(Stream<Entity> entities) {
        this.delegate().addWorldGenChunkEntities(entities);
	}

	@Override
	public void startTickingChunk(LevelChunk chunk) {
        this.delegate().startTickingChunk(chunk);
	}

	@Override
	public void onStructureStartsAvailable(ChunkAccess chunk) {
        this.delegate().onStructureStartsAvailable(chunk);
	}

	@Override
	public void close() throws IOException {
        this.delegate().close();
	}

	@Override
	public String gatherChunkSourceStats() {
		return this.delegate().gatherChunkSourceStats();
	}

	@Override
	public boolean areEntitiesLoaded(long chunkPos) {
		return this.delegate().areEntitiesLoaded(chunkPos);
	}

	@Override
	public boolean isPositionEntityTicking(BlockPos pos) {
		return this.delegate().isPositionEntityTicking(pos);
	}

	@Override
	public boolean anyPlayerCloseEnoughForSpawning(BlockPos pos) {
		return this.delegate().anyPlayerCloseEnoughForSpawning(pos);
	}

	@Override
	public boolean anyPlayerCloseEnoughForSpawning(ChunkPos pos) {
		return this.delegate().anyPlayerCloseEnoughForSpawning(pos);
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return this.delegate().enabledFeatures();
	}

	@Override
	public ServerScoreboard getScoreboard() {
		return this.delegate().getScoreboard();
	}

	@Override
	public ServerChunkCache getChunkSource() {
		return this.delegate().getChunkSource();
	}

	@Override
	public LevelTicks<Fluid> getFluidTicks() {
		return this.delegate().getFluidTicks();
	}

	@Override
	public LevelTicks<Block> getBlockTicks() {
		return this.delegate().getBlockTicks();
	}
}
