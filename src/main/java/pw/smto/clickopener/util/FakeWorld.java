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

import net.minecraft.component.type.MapIdComponent;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.registry.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.dimension.PortalForcer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import com.mojang.datafixers.util.Pair;

import io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import pw.smto.clickopener.impl.BlockOpenContext;
import pw.smto.clickopener.impl.BlockScreenOpener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityInteraction;
import net.minecraft.entity.InteractionObserver;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;
import net.minecraft.world.BlockStateRaycastContext;
import net.minecraft.world.BlockView;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.tick.TickPriority;
import net.minecraft.world.tick.WorldTickScheduler;

@SuppressWarnings({"deprecation", "java:S5803"})
public class FakeWorld extends ServerWorld {
	private static final long RANDOM_OFFSET = UnsafeAccess.fieldOffset(World.class, FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_1937", "field_9229", "Lnet/minecraft/class_5819;"));
	private static final long CONTEXT_OFFSET = UnsafeAccess.fieldOffset(FakeWorld.class, "context");

	@SuppressWarnings("unused")
    private final BlockOpenContext context;

	private FakeWorld() {
		super(null, null, null, null, null, null, null, false, 0, null, false, null);
		throw new IllegalStateException("FakeWorld constructor should not be used.");
	}

	public static FakeWorld create(BlockOpenContext context) {
		try {
			var fakeWorld = (FakeWorld) UnsafeAccess.UNSAFE.allocateInstance(FakeWorld.class);
			//set public fields from world
			UnsafeAccess.UNSAFE.putObject(fakeWorld, FakeWorld.RANDOM_OFFSET, context.player().getServerWorld().random);
			//isClient is false by default
			UnsafeAccess.UNSAFE.putObject(fakeWorld, FakeWorld.CONTEXT_OFFSET, context);
			return fakeWorld;
		} catch (InstantiationException e) {
			throw new ItemOpenException("Failed to allocate instance of FakeWorld.", e);
		}
	}

	private ServerWorld delegate() {
		return this.context.player().getServerWorld();
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
	public boolean setBlockState(BlockPos pos, BlockState state, int flags) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(state);
			return true;
		}, () -> this.delegate().setBlockState(pos, state, flags));
	}

	@Override
	public boolean setBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(state);
			return true;
		}, () -> this.delegate().setBlockState(pos, state, flags, maxUpdateDepth));
	}

	@Override
	public boolean setBlockState(BlockPos pos, BlockState state) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(state);
			return true;
		}, () -> this.delegate().setBlockState(pos, state));
	}

	@Override
	public boolean removeBlock(BlockPos pos, boolean move) {
		return this.ifHandlesOrElse(pos, () -> {
            this.context.setBlockState(Blocks.AIR.getDefaultState());
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
	public void addSyncedBlockEvent(BlockPos pos, Block block, int type, int data) {
        this.ifHandlesOrElse(pos, () -> this.context.getBlockState().onSyncedBlockEvent(this, pos, type, data), () -> this.delegate().addSyncedBlockEvent(pos, block, type, data));
	}

	@Override
	protected EntityLookup<Entity> getEntityLookup() {
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
	public void updateComparators(BlockPos pos, Block block) {
		if (this.context.handles(pos)) return;
        this.delegate().updateComparators(pos, block);
	}

	@Override
	public void markDirty(BlockPos pos) {
        this.ifHandlesOrElse(pos, () -> this.context.openerConsumer(BlockScreenOpener::onMarkDirty), () -> this.delegate().markDirty(pos));
	}

	@Override
	public boolean isSpaceEmpty(Box box) {
		return true;
	}

	/*
	 * Delegated methods
	 */

	@Override
	public float getMoonSize() {
		return this.delegate().getMoonSize();
	}

	@Override
	public boolean isValidForSetBlock(BlockPos pos) {
		return this.delegate().isValidForSetBlock(pos);
	}

	@Override
	public void spawnEntityAndPassengers(Entity entity) {
        this.delegate().spawnEntityAndPassengers(entity);
	}

	@Override
	public int getStrongRedstonePower(BlockPos pos, Direction direction) {
		return this.delegate().getStrongRedstonePower(pos, direction);
	}

	@Override
	public float getSkyAngle(float tickDelta) {
		return this.delegate().getSkyAngle(tickDelta);
	}

	@Override
	public void setCurrentlyGeneratingStructureName(Supplier<String> structureName) {
        this.delegate().setCurrentlyGeneratingStructureName(structureName);
	}

	@Override
	public int getLightLevel(LightType type, BlockPos pos) {
		return this.delegate().getLightLevel(type, pos);
	}

	@Override
	public int getReceivedStrongRedstonePower(BlockPos pos) {
		return this.delegate().getReceivedStrongRedstonePower(pos);
	}

	@Override
	public int getMoonPhase() {
		return this.delegate().getMoonPhase();
	}

	@Override
	public int getBaseLightLevel(BlockPos pos, int ambientDarkness) {
		return this.delegate().getBaseLightLevel(pos, ambientDarkness);
	}

	@Override
	public boolean isSkyVisible(BlockPos pos) {
		return this.delegate().isSkyVisible(pos);
	}

	@Override
	public List<VoxelShape> getEntityCollisions(Entity entity, Box box) {
		return this.delegate().getEntityCollisions(entity, box);
	}

	@Override
	public int countVerticalSections() {
		return this.delegate().countVerticalSections();
	}

	@Override
	public boolean doesNotIntersectEntities(Entity except, VoxelShape shape) {
		return this.delegate().doesNotIntersectEntities(except, shape);
	}

	@Override
	public long getLunarTime() {
		return this.delegate().getLunarTime();
	}

	@Override
	public boolean canPlace(BlockState state, BlockPos pos, ShapeContext context) {
		return this.delegate().canPlace(state, pos, context);
	}

	@Override
	public BlockPos getTopPosition(Heightmap.Type heightmap, BlockPos pos) {
		return this.delegate().getTopPosition(heightmap, pos);
	}

	@Override
	public @Nullable Object getBlockEntityRenderData(BlockPos pos) {
		return this.delegate().getBlockEntityRenderData(pos);
	}

	@Override
	public int getBottomSectionCoord() {
		return this.delegate().getBottomSectionCoord();
	}

	@Override
	public int getEmittedRedstonePower(BlockPos pos, Direction direction, boolean onlyFromGate) {
		return this.delegate().getEmittedRedstonePower(pos, direction, onlyFromGate);
	}

	@Override
	public <T extends Entity> List<T> getEntitiesByClass(Class<T> entityClass, Box box,
			Predicate<? super T> predicate) {
		return this.delegate().getEntitiesByClass(entityClass, box, predicate);
	}

	@Override
	public boolean doesNotIntersectEntities(Entity entity) {
		return this.delegate().doesNotIntersectEntities(entity);
	}

	@Override
	public boolean isSpaceEmpty(Entity entity) {
		return this.delegate().isSpaceEmpty(entity);
	}

	@Override
	public void scheduleBlockTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        this.delegate().scheduleBlockTick(pos, block, delay, priority);
	}

	@Override
	public RegistryEntry<Biome> getBiome(BlockPos pos) {
		return this.delegate().getBiome(pos);
	}

	@Override
	public int getTopSectionCoord() {
		return this.delegate().getTopSectionCoord();
	}

	@Override
	public boolean isSpaceEmpty(Entity entity, Box box) {
		return this.delegate().isSpaceEmpty(entity, box);
	}

	@Override
	public Stream<BlockState> getStatesInBoxIfLoaded(Box box) {
		return this.delegate().getStatesInBoxIfLoaded(box);
	}

	@Override
	public void scheduleBlockTick(BlockPos pos, Block block, int delay) {
        this.delegate().scheduleBlockTick(pos, block, delay);
	}

	@Override
	public int getLuminance(BlockPos pos) {
		return this.delegate().getLuminance(pos);
	}

	@Override
	public boolean isEmittingRedstonePower(BlockPos pos, Direction direction) {
		return this.delegate().isEmittingRedstonePower(pos, direction);
	}

	@Override
	public List<Entity> getOtherEntities(Entity except, Box box) {
		return this.delegate().getOtherEntities(except, box);
	}

	@Override
	public Stream<BlockState> getStatesInBox(Box box) {
		return this.delegate().getStatesInBox(box);
	}

	@Override
	public void scheduleFluidTick(BlockPos pos, Fluid fluid, int delay, TickPriority priority) {
        this.delegate().scheduleFluidTick(pos, fluid, delay, priority);
	}

	@Override
	public int getEmittedRedstonePower(BlockPos pos, Direction direction) {
		return this.delegate().getEmittedRedstonePower(pos, direction);
	}

	@Override
	public BlockHitResult raycast(BlockStateRaycastContext context) {
		return this.delegate().raycast(context);
	}

	@Override
	public boolean isOutOfHeightLimit(BlockPos pos) {
		return this.delegate().isOutOfHeightLimit(pos);
	}

	@Override
	public int hashCode() {
		return this.delegate().hashCode();
	}

	@Override
	public int getColor(BlockPos pos, ColorResolver colorResolver) {
		return this.delegate().getColor(pos, colorResolver);
	}

	@Override
	public void scheduleFluidTick(BlockPos pos, Fluid fluid, int delay) {
        this.delegate().scheduleFluidTick(pos, fluid, delay);
	}

	@Override
	public RegistryEntry<Biome> getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
		return this.delegate().getBiomeForNoiseGen(biomeX, biomeY, biomeZ);
	}

	@Override
	public Iterable<VoxelShape> getCollisions(Entity entity, Box box) {
		return this.delegate().getCollisions(entity, box);
	}

	@Override
	public boolean isReceivingRedstonePower(BlockPos pos) {
		return this.delegate().isReceivingRedstonePower(pos);
	}

	@Override
	public boolean isOutOfHeightLimit(int y) {
		return this.delegate().isOutOfHeightLimit(y);
	}

	@Override
	public Difficulty getDifficulty() {
		return this.delegate().getDifficulty();
	}

	@Override
	public Iterable<VoxelShape> getBlockCollisions(Entity entity, Box box) {
		return this.delegate().getBlockCollisions(entity, box);
	}

	@Override
	public boolean isChunkLoaded(int chunkX, int chunkZ) {
		return this.delegate().isChunkLoaded(chunkX, chunkZ);
	}

	@Override
	public BlockHitResult raycast(RaycastContext context) {
		return this.delegate().raycast(context);
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
	public int getReceivedRedstonePower(BlockPos pos) {
		return this.delegate().getReceivedRedstonePower(pos);
	}

	@Override
	public boolean breakBlock(BlockPos pos, boolean drop) {
		return this.delegate().breakBlock(pos, drop);
	}

	@Override
	public boolean canCollide(Entity entity, Box box) {
		return this.delegate().canCollide(entity, box);
	}

	@Override
	public int getBottomY() {
		return this.delegate().getBottomY();
	}

	@Override
	public int getHeight() {
		return this.delegate().getHeight();
	}

	@Override
	public int sectionCoordToIndex(int coord) {
		return this.delegate().sectionCoordToIndex(coord);
	}

	@Override
	public @UnknownNullability RegistryEntry<Biome> getBiomeFabric(BlockPos pos) {
		return this.delegate().getBiomeFabric(pos);
	}

	@Override
	public <T extends Entity> List<T> getNonSpectatingEntities(Class<T> entityClass, Box box) {
		return this.delegate().getNonSpectatingEntities(entityClass, box);
	}

	@Override
	public Optional<BlockPos> findSupportingBlockPos(Entity entity, Box box) {
		return this.delegate().findSupportingBlockPos(entity, box);
	}

	@Override
	public int sectionIndexToCoord(int index) {
		return this.delegate().sectionIndexToCoord(index);
	}

	@Override
	public boolean isAir(BlockPos pos) {
		return this.delegate().isAir(pos);
	}

	@Override
	public boolean breakBlock(BlockPos pos, boolean drop, Entity breakingEntity) {
		return this.delegate().breakBlock(pos, drop, breakingEntity);
	}

	@Override
	public boolean isSkyVisibleAllowingSea(BlockPos pos) {
		return this.delegate().isSkyVisibleAllowingSea(pos);
	}

	@Override
	public void syncWorldEvent(int eventId, BlockPos pos, int data) {
        this.delegate().syncWorldEvent(eventId, pos, data);
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
	public boolean equals(Object obj) {
		return this.delegate().equals(obj);
	}

	@Override
	public void emitGameEvent(Entity entity, RegistryEntry<GameEvent> event, Vec3d pos) {
        this.delegate().emitGameEvent(entity, event, pos);
	}

	@Override
	public void emitGameEvent(Entity entity, RegistryEntry<GameEvent> event, BlockPos pos) {
        this.delegate().emitGameEvent(entity, event, pos);
	}

	@Override
	public Optional<Vec3d> findClosestCollision(Entity entity, VoxelShape shape, Vec3d target, double x, double y,
			double z) {
		return this.delegate().findClosestCollision(entity, shape, target, x, y, z);
	}

	@Override
	public PlayerEntity getClosestPlayer(double x, double y, double z, double maxDistance,
			Predicate<Entity> targetPredicate) {
		return this.delegate().getClosestPlayer(x, y, z, maxDistance, targetPredicate);
	}

	@Override
	public BlockHitResult raycastBlock(Vec3d start, Vec3d end, BlockPos pos, VoxelShape shape, BlockState state) {
		return this.delegate().raycastBlock(start, end, pos, shape, state);
	}

	@Override
	public void emitGameEvent(RegistryEntry<GameEvent> event, BlockPos pos, GameEvent.Emitter emitter) {
        this.delegate().emitGameEvent(event, pos, emitter);
	}

	@Override
	public float getPhototaxisFavor(BlockPos pos) {
		return this.delegate().getPhototaxisFavor(pos);
	}

	@Override
	public float getBrightness(BlockPos pos) {
		return this.delegate().getBrightness(pos);
	}

	@Override
	public double getDismountHeight(VoxelShape blockCollisionShape,
			Supplier<VoxelShape> belowBlockCollisionShapeGetter) {
		return this.delegate().getDismountHeight(blockCollisionShape, belowBlockCollisionShapeGetter);
	}

	@Override
	public PlayerEntity getClosestPlayer(Entity entity, double maxDistance) {
		return this.delegate().getClosestPlayer(entity, maxDistance);
	}

	@Override
	public PlayerEntity getClosestPlayer(double x, double y, double z, double maxDistance, boolean ignoreCreative) {
		return this.delegate().getClosestPlayer(x, y, z, maxDistance, ignoreCreative);
	}

	@Override
	public Chunk getChunk(BlockPos pos) {
		return this.delegate().getChunk(pos);
	}

	@Override
	public double getDismountHeight(BlockPos pos) {
		return this.delegate().getDismountHeight(pos);
	}

	@Override
	public boolean isPlayerInRange(double x, double y, double z, double range) {
		return this.delegate().isPlayerInRange(x, y, z, range);
	}

	@Override
	public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus status) {
		return this.delegate().getChunk(chunkX, chunkZ, status);
	}

	@Override
	public boolean isWater(BlockPos pos) {
		return this.delegate().isWater(pos);
	}

	@Override
	public PlayerEntity getClosestPlayer(TargetPredicate targetPredicate, LivingEntity entity) {
		return this.delegate().getClosestPlayer(targetPredicate, entity);
	}

	@Override
	public boolean containsFluid(Box box) {
		return this.delegate().containsFluid(box);
	}

	@Override
	public PlayerEntity getClosestPlayer(TargetPredicate targetPredicate, LivingEntity entity, double x, double y,
			double z) {
		return this.delegate().getClosestPlayer(targetPredicate, entity, x, y, z);
	}

	@Override
	public PlayerEntity getClosestPlayer(TargetPredicate targetPredicate, double x, double y, double z) {
		return this.delegate().getClosestPlayer(targetPredicate, x, y, z);
	}

	@Override
	public boolean isClient() {
		return this.delegate().isClient();
	}

	@Override
	public <T extends LivingEntity> T getClosestEntity(Class<? extends T> entityClass, TargetPredicate targetPredicate,
			LivingEntity entity, double x, double y, double z, Box box) {
		return this.delegate().getClosestEntity(entityClass, targetPredicate, entity, x, y, z, box);
	}

	@Override
	public boolean isInBuildLimit(BlockPos pos) {
		return this.delegate().isInBuildLimit(pos);
	}

	@Override
	public int getLightLevel(BlockPos pos) {
		return this.delegate().getLightLevel(pos);
	}

	@Override
	public int getLightLevel(BlockPos pos, int ambientDarkness) {
		return this.delegate().getLightLevel(pos, ambientDarkness);
	}

	@Override
	public <T extends LivingEntity> T getClosestEntity(List<? extends T> entityList, TargetPredicate targetPredicate,
			LivingEntity entity, double x, double y, double z) {
		return this.delegate().getClosestEntity(entityList, targetPredicate, entity, x, y, z);
	}

	@Override
	public boolean isPosLoaded(int x, int z) {
		return this.delegate().isPosLoaded(x, z);
	}

	@Override
	public boolean isChunkLoaded(BlockPos pos) {
		return this.delegate().isChunkLoaded(pos);
	}

	@Override
	public boolean isRegionLoaded(BlockPos min, BlockPos max) {
		return this.delegate().isRegionLoaded(min, max);
	}

	@Override
	public List<PlayerEntity> getPlayers(TargetPredicate targetPredicate, LivingEntity entity, Box box) {
		return this.delegate().getPlayers(targetPredicate, entity, box);
	}

	@Override
	public boolean isRegionLoaded(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		return this.delegate().isRegionLoaded(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	public <T extends LivingEntity> List<T> getTargets(Class<T> entityClass, TargetPredicate targetPredicate,
			LivingEntity targetingEntity, Box box) {
		return this.delegate().getTargets(entityClass, targetPredicate, targetingEntity, box);
	}

	@Override
	public boolean isRegionLoaded(int minX, int minZ, int maxX, int maxZ) {
		return this.delegate().isRegionLoaded(minX, minZ, maxX, maxZ);
	}

	@Override
	public PlayerEntity getPlayerByUuid(UUID uuid) {
		return this.delegate().getPlayerByUuid(uuid);
	}

	@Override
	public WorldChunk getWorldChunk(BlockPos pos) {
		return this.delegate().getWorldChunk(pos);
	}

	@Override
	public <T> RegistryWrapper<T> createCommandRegistryWrapper(
			RegistryKey<? extends Registry<? extends T>> registryRef) {
		return this.delegate().createCommandRegistryWrapper(registryRef);
	}

	@Override
	public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
		return this.delegate().getChunk(chunkX, chunkZ, leastStatus, create);
	}

	@Override
	public boolean breakBlock(BlockPos pos, boolean drop, Entity breakingEntity, int maxUpdateDepth) {
		return this.delegate().breakBlock(pos, drop, breakingEntity, maxUpdateDepth);
	}

	@Override
	public void setEnderDragonFight(EnderDragonFight enderDragonFight) {
        this.delegate().setEnderDragonFight(enderDragonFight);
	}

	@Override
	public void addBlockBreakParticles(BlockPos pos, BlockState state) {
        this.delegate().addBlockBreakParticles(pos, state);
	}

	@Override
	public void setWeather(int clearDuration, int rainDuration, boolean raining, boolean thundering) {
        this.delegate().setWeather(clearDuration, rainDuration, raining, thundering);
	}

	@Override
	public void scheduleBlockRerenderIfNeeded(BlockPos pos, BlockState old, BlockState updated) {
        this.delegate().scheduleBlockRerenderIfNeeded(pos, old, updated);
	}

	@Override
	public RegistryEntry<Biome> getGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ) {
		return this.delegate().getGeneratorStoredBiome(biomeX, biomeY, biomeZ);
	}

	@Override
	public StructureAccessor getStructureAccessor() {
		return this.delegate().getStructureAccessor();
	}

	@Override
	public void tick(BooleanSupplier shouldKeepTicking) {
        this.delegate().tick(shouldKeepTicking);
	}

	@Override
	public int getTopY(Heightmap.Type heightmap, int x, int z) {
		return this.delegate().getTopY(heightmap, x, z);
	}

	@Override
	public LightingProvider getLightingProvider() {
		return this.delegate().getLightingProvider();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return this.delegate().getFluidState(pos);
	}

	@Override
	public boolean isDay() {
		return this.delegate().isDay();
	}

	@Override
	public boolean isNight() {
		return this.delegate().isNight();
	}

	@Override
	public void playSound(Entity except, BlockPos pos, SoundEvent sound, SoundCategory category, float volume,
			float pitch) {
        this.delegate().playSound(except, pos, sound, category, volume, pitch);
	}

	@Override
	public boolean shouldTickBlocksInChunk(long chunkPos) {
		return this.delegate().shouldTickBlocksInChunk(chunkPos);
	}

	@Override
	public void setTimeOfDay(long timeOfDay) {
        this.delegate().setTimeOfDay(timeOfDay);
	}

	@Override
	public void tickSpawners(boolean spawnMonsters, boolean spawnAnimals) {
        this.delegate().tickSpawners(spawnMonsters, spawnAnimals);
	}

	@Override
	public float getSkyAngleRadians(float tickDelta) {
		return this.delegate().getSkyAngleRadians(tickDelta);
	}

	@Override
	public void addBlockEntityTicker(BlockEntityTickInvoker ticker) {
        this.delegate().addBlockEntityTicker(ticker);
	}

	@Override
	public void tickChunk(WorldChunk chunk, int randomTickSpeed) {
        this.delegate().tickChunk(chunk, randomTickSpeed);
	}

	@Override
	public <T extends Entity> void tickEntity(Consumer<T> tickConsumer, T entity) {
        this.delegate().tickEntity(tickConsumer, entity);
	}

	@Override
	public boolean shouldUpdatePostDeath(Entity entity) {
		return this.delegate().shouldUpdatePostDeath(entity);
	}

	@Override
	public boolean shouldTickBlockPos(BlockPos pos) {
		return this.delegate().shouldTickBlockPos(pos);
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
	public void addBlockEntity(BlockEntity blockEntity) {
        this.delegate().addBlockEntity(blockEntity);
	}

	@Override
	public boolean isInBlockTick() {
		return this.delegate().isInBlockTick();
	}

	@Override
	public boolean isSleepingEnabled() {
		return this.delegate().isSleepingEnabled();
	}

	@Override
	public void removeBlockEntity(BlockPos pos) {
        this.delegate().removeBlockEntity(pos);
	}

	@Override
	public boolean isDirectionSolid(BlockPos pos, Entity entity, Direction direction) {
		return this.delegate().isDirectionSolid(pos, entity, direction);
	}

	@Override
	public void updateSleepingPlayers() {
        this.delegate().updateSleepingPlayers();
	}

	@Override
	public boolean isTopSolid(BlockPos pos, Entity entity) {
		return this.delegate().isTopSolid(pos, entity);
	}

	@Override
	public void calculateAmbientDarkness() {
        this.delegate().calculateAmbientDarkness();
	}

	/*
	@Override
	public void setMobSpawnOptions(boolean spawnMonsters, boolean spawnAnimals) {
		delegate().setMobSpawnOptions(spawnMonsters, spawnAnimals);
	}

	 */

	@Override
	public BlockPos getSpawnPos() {
		return this.delegate().getSpawnPos();
	}

	@Override
	public float getSpawnAngle() {
		return this.delegate().getSpawnAngle();
	}

	@Override
	public BlockView getChunkAsView(int chunkX, int chunkZ) {
		return this.delegate().getChunkAsView(chunkX, chunkZ);
	}

	@Override
	public List<Entity> getOtherEntities(Entity except, Box box, Predicate<? super Entity> predicate) {
		return this.delegate().getOtherEntities(except, box, predicate);
	}

	@Override
	public <T extends Entity> List<T> getEntitiesByType(TypeFilter<Entity, T> filter, Box box,
			Predicate<? super T> predicate) {
		return this.delegate().getEntitiesByType(filter, box, predicate);
	}

	@Override
	public <T extends Entity> void collectEntitiesByType(TypeFilter<Entity, T> filter, Box box,
			Predicate<? super T> predicate, List<? super T> result) {
        this.delegate().collectEntitiesByType(filter, box, predicate, result);
	}

	@Override
	public <T extends Entity> void collectEntitiesByType(TypeFilter<Entity, T> filter, Box box,
			Predicate<? super T> predicate, List<? super T> result, int limit) {
        this.delegate().collectEntitiesByType(filter, box, predicate, result, limit);
	}

	@Override
	public void resetIdleTimeout() {
        this.delegate().resetIdleTimeout();
	}

	@Override
	public void tickEntity(Entity entity) {
        this.delegate().tickEntity(entity);
	}

	@Override
	public int getSeaLevel() {
		return this.delegate().getSeaLevel();
	}

	@Override
	public void disconnect() {
        this.delegate().disconnect();
	}

	@Override
	public long getTime() {
		return this.delegate().getTime();
	}

	@Override
	public long getTimeOfDay() {
		return this.delegate().getTimeOfDay();
	}

	@Override
	public boolean canEntityModifyAt(Entity entity, BlockPos pos) {
		return this.delegate().canEntityModifyAt(entity, pos);
	}

	@Override
	public void save(ProgressListener progressListener, boolean flush, boolean savingDisabled) {
        this.delegate().save(progressListener, flush, savingDisabled);
	}

	@Override
	public WorldProperties getLevelProperties() {
		return this.delegate().getLevelProperties();
	}

	@Override
	public GameRules getGameRules() {
		return this.delegate().getGameRules();
	}

	@Override
	public float getThunderGradient(float delta) {
		return this.delegate().getThunderGradient(delta);
	}

	@Override
	public <T extends Entity> List<? extends T> getEntitiesByType(TypeFilter<Entity, T> filter,
			Predicate<? super T> predicate) {
		return this.delegate().getEntitiesByType(filter, predicate);
	}

	@Override
	public void setThunderGradient(float thunderGradient) {
        this.delegate().setThunderGradient(thunderGradient);
	}

	@Override
	public float getRainGradient(float delta) {
		return this.delegate().getRainGradient(delta);
	}

	@Override
	public void setRainGradient(float rainGradient) {
        this.delegate().setRainGradient(rainGradient);
	}

	@Override
	public boolean isThundering() {
		return this.delegate().isThundering();
	}

	@Override
	public <T extends Entity> void collectEntitiesByType(TypeFilter<Entity, T> filter, Predicate<? super T> predicate,
			List<? super T> result) {
        this.delegate().collectEntitiesByType(filter, predicate, result);
	}

	@Override
	public boolean isRaining() {
		return this.delegate().isRaining();
	}

	@Override
	public <T extends Entity> void collectEntitiesByType(TypeFilter<Entity, T> filter, Predicate<? super T> predicate,
			List<? super T> result, int limit) {
        this.delegate().collectEntitiesByType(filter, predicate, result, limit);
	}

	@Override
	public boolean hasRain(BlockPos pos) {
		return this.delegate().hasRain(pos);
	}

	@Override
	public List<? extends EnderDragonEntity> getAliveEnderDragons() {
		return this.delegate().getAliveEnderDragons();
	}

	@Override
	public List<ServerPlayerEntity> getPlayers(Predicate<? super ServerPlayerEntity> predicate) {
		return this.delegate().getPlayers(predicate);
	}

	@Override
	public CrashReportSection addDetailsToCrashReport(CrashReport report) {
		return this.delegate().addDetailsToCrashReport(report);
	}

	@Override
	public List<ServerPlayerEntity> getPlayers(Predicate<? super ServerPlayerEntity> predicate, int limit) {
		return this.delegate().getPlayers(predicate, limit);
	}

	@Override
	public ServerPlayerEntity getRandomAlivePlayer() {
		return this.delegate().getRandomAlivePlayer();
	}

	//@Override
	//public void addFireworkParticle(double x, double y, double z, double velocityX, double velocityY, double velocityZ,
	//		NbtCompound nbt) {
	//	delegate().addFireworkParticle(x, y, z, velocityX, velocityY, velocityZ, nbt);
	//}

	@Override
	public boolean spawnEntity(Entity entity) {
		return this.delegate().spawnEntity(entity);
	}

	@Override
	public boolean tryLoadEntity(Entity entity) {
		return this.delegate().tryLoadEntity(entity);
	}

	@Override
	public void onDimensionChanged(Entity entity) {
        this.delegate().onDimensionChanged(entity);
	}

	@Override
	public LocalDifficulty getLocalDifficulty(BlockPos pos) {
		return this.delegate().getLocalDifficulty(pos);
	}

	//@Override
	//public void onPlayerTeleport(ServerPlayerEntity player) {
	//	delegate().onPlayerTeleport(player);
	//}

	@Override
	public int getAmbientDarkness() {
		return this.delegate().getAmbientDarkness();
	}

	//@Override
	//public void onPlayerChangeDimension(ServerPlayerEntity player) {
	//	delegate().onPlayerChangeDimension(player);
	//}

	@Override
	public void setLightningTicksLeft(int lightningTicksLeft) {
        this.delegate().setLightningTicksLeft(lightningTicksLeft);
	}

	@Override
	public WorldBorder getWorldBorder() {
		return this.delegate().getWorldBorder();
	}

	@Override
	public void sendPacket(Packet<?> packet) {
        this.delegate().sendPacket(packet);
	}

	@Override
	public void onPlayerConnected(ServerPlayerEntity player) {
        this.delegate().onPlayerConnected(player);
	}

	@Override
	public DimensionType getDimension() {
		return this.delegate().getDimension();
	}

	//@Override
	//public RegistryKey<DimensionType> getDimensionKey() {
	//	return delegate().getDimensionKey();
	//}

	@Override
	public void onPlayerRespawned(ServerPlayerEntity player) {
        this.delegate().onPlayerRespawned(player);
	}

	@Override
	public RegistryEntry<DimensionType> getDimensionEntry() {
		return this.delegate().getDimensionEntry();
	}

	@Override
	public RegistryKey<World> getRegistryKey() {
		return this.delegate().getRegistryKey();
	}

	@Override
	public Random getRandom() {
		return this.delegate().getRandom();
	}

	@Override
	public boolean testBlockState(BlockPos pos, Predicate<BlockState> state) {
		return this.delegate().testBlockState(pos, state);
	}

	@Override
	public boolean testFluidState(BlockPos pos, Predicate<FluidState> state) {
		return this.delegate().testFluidState(pos, state);
	}

	@Override
	public BlockPos getRandomPosInChunk(int x, int y, int z, int i) {
		return this.delegate().getRandomPosInChunk(x, y, z, i);
	}

	@Override
	public boolean spawnNewEntityAndPassengers(Entity entity) {
		return this.delegate().spawnNewEntityAndPassengers(entity);
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
	public BiomeAccess getBiomeAccess() {
		return this.delegate().getBiomeAccess();
	}

	@Override
	public void unloadEntities(WorldChunk chunk) {
        this.delegate().unloadEntities(chunk);
	}

	@Override
	public void removePlayer(ServerPlayerEntity player, Entity.RemovalReason reason) {
        this.delegate().removePlayer(player, reason);
	}

	@Override
	public long getTickOrder() {
		return this.delegate().getTickOrder();
	}

	@Override
	public void setBlockBreakingInfo(int entityId, BlockPos pos, int progress) {
        this.delegate().setBlockBreakingInfo(entityId, pos, progress);
	}

	@Override
	public DynamicRegistryManager getRegistryManager() {
		return this.delegate().getRegistryManager();
	}

	@Override
	public DamageSources getDamageSources() {
		return this.delegate().getDamageSources();
	}

	@Override
	public WorldChunk getChunk(int chunkX, int chunkZ) {
		return this.delegate().getChunk(chunkX, chunkZ);
	}

	@Override
	public void playSound(@Nullable Entity source, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
        this.delegate().playSound(source, x, y, z, sound, category, volume, pitch, seed);
	}

	@Override
	public void playSoundFromEntity(@Nullable Entity source, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
        this.delegate().playSoundFromEntity(source, entity, sound, category, volume, pitch, seed);
	}

	@Override
	public void syncGlobalEvent(int eventId, BlockPos pos, int data) {
        this.delegate().syncGlobalEvent(eventId, pos, data);
	}

	@Override
	public void syncWorldEvent(@Nullable Entity source, int eventId, BlockPos pos, int data) {
        this.delegate().syncWorldEvent(source, eventId, pos, data);
	}

	@Override
	public int getLogicalHeight() {
		return this.delegate().getLogicalHeight();
	}

	@Override
	public void emitGameEvent(RegistryEntry<GameEvent> event, Vec3d emitterPos, GameEvent.Emitter emitter) {
        this.delegate().emitGameEvent(event, emitterPos, emitter);
	}

	@Override
	public void updateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        this.delegate().updateListeners(pos, oldState, newState, flags);
	}

	@Override
	public void updateNeighborsAlways(BlockPos pos, Block sourceBlock, @Nullable WireOrientation orientation) {
        this.delegate().updateNeighborsAlways(pos, sourceBlock, orientation);
	}

	@Override
	public void sendEntityStatus(Entity entity, byte status) {
        this.delegate().sendEntityStatus(entity, status);
	}

	@Override
	public void sendEntityDamage(Entity entity, DamageSource damageSource) {
        this.delegate().sendEntityDamage(entity, damageSource);
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
	public StructureTemplateManager getStructureTemplateManager() {
		return this.delegate().getStructureTemplateManager();
	}

	@Override
	public <T extends ParticleEffect> int spawnParticles(T particle, double x, double y, double z, int count,
			double deltaX, double deltaY, double deltaZ, double speed) {
		return this.delegate().spawnParticles(particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}

	@Override
	public <T extends ParticleEffect> boolean spawnParticles(
			ServerPlayerEntity viewer,
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
		return this.delegate().spawnParticles(viewer, parameters, force, important, x, y, z, count, offsetX, offsetY, offsetZ, speed);
	}

	@Override
	public Entity getEntityById(int id) {
		return this.delegate().getEntityById(id);
	}

	@Override
	public Entity getEntity(UUID uuid) {
		return this.delegate().getEntity(uuid);
	}

	@Override
	public BlockPos locateStructure(TagKey<Structure> structureTag, BlockPos pos, int radius,
			boolean skipReferencedStructures) {
		return this.delegate().locateStructure(structureTag, pos, radius, skipReferencedStructures);
	}

	@Override
	public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(Predicate<RegistryEntry<Biome>> predicate, BlockPos pos,
			int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval) {
		return this.delegate().locateBiome(predicate, pos, radius, horizontalBlockCheckInterval, verticalBlockCheckInterval);
	}

	@Override
	public ServerRecipeManager getRecipeManager() {
		return this.delegate().getRecipeManager();
	}

	@Override
	public boolean isSavingDisabled() {
		return this.delegate().isSavingDisabled();
	}

	@Override
	public PersistentStateManager getPersistentStateManager() {
		return this.delegate().getPersistentStateManager();
	}

	@Override
	public MapState getMapState(MapIdComponent id) {
		return this.delegate().getMapState(id);
	}

	@Override
	public void putMapState(MapIdComponent id, MapState state) {
        this.delegate().putMapState(id, state);
	}

	@Override
	public MapIdComponent increaseAndGetMapId() {
		return this.delegate().increaseAndGetMapId();
	}

	@Override
	public void setSpawnPos(BlockPos pos, float angle) {
        this.delegate().setSpawnPos(pos, angle);
	}

	@Override
	public LongSet getForcedChunks() {
		return this.delegate().getForcedChunks();
	}

	@Override
	public boolean setChunkForced(int x, int z, boolean forced) {
		return this.delegate().setChunkForced(x, z, forced);
	}

	@Override
	public List<ServerPlayerEntity> getPlayers() {
		return this.delegate().getPlayers();
	}

	@Override
	public PointOfInterestStorage getPointOfInterestStorage() {
		return this.delegate().getPointOfInterestStorage();
	}

	@Override
	public boolean isNearOccupiedPointOfInterest(BlockPos pos) {
		return this.delegate().isNearOccupiedPointOfInterest(pos);
	}

	@Override
	public boolean isNearOccupiedPointOfInterest(ChunkSectionPos sectionPos) {
		return this.delegate().isNearOccupiedPointOfInterest(sectionPos);
	}

	@Override
	public boolean isNearOccupiedPointOfInterest(BlockPos pos, int maxDistance) {
		return this.delegate().isNearOccupiedPointOfInterest(pos, maxDistance);
	}

	@Override
	public int getOccupiedPointOfInterestDistance(ChunkSectionPos pos) {
		return this.delegate().getOccupiedPointOfInterestDistance(pos);
	}

	@Override
	public RaidManager getRaidManager() {
		return this.delegate().getRaidManager();
	}

	@Override
	public Raid getRaidAt(BlockPos pos) {
		return this.delegate().getRaidAt(pos);
	}

	@Override
	public boolean hasRaidAt(BlockPos pos) {
		return this.delegate().hasRaidAt(pos);
	}

	@Override
	public void handleInteraction(EntityInteraction interaction, Entity entity, InteractionObserver observer) {
        this.delegate().handleInteraction(interaction, entity, observer);
	}

	@Override
	public void dump(Path path) throws IOException {
        this.delegate().dump(path);
	}

	@Override
	public void clearUpdatesInArea(BlockBox box) {
        this.delegate().clearUpdatesInArea(box);
	}

	@Override
	public void updateNeighbors(BlockPos pos, Block block) {
        this.delegate().updateNeighbors(pos, block);
	}

	@Override
	public float getBrightness(Direction direction, boolean shaded) {
		return this.delegate().getBrightness(direction, shaded);
	}

	@Override
	public Iterable<Entity> iterateEntities() {
		return this.delegate().iterateEntities();
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
	public EnderDragonFight getEnderDragonFight() {
		return this.delegate().getEnderDragonFight();
	}

	@Override
	public ServerWorld toServerWorld() {
		return this.delegate().toServerWorld();
	}

	@Override
	public String getDebugString() {
		return this.delegate().getDebugString();
	}

	@Override
	public void loadEntities(Stream<Entity> entities) {
        this.delegate().loadEntities(entities);
	}

	@Override
	public void addEntities(Stream<Entity> entities) {
        this.delegate().addEntities(entities);
	}

	@Override
	public void disableTickSchedulers(WorldChunk chunk) {
        this.delegate().disableTickSchedulers(chunk);
	}

	@Override
	public void cacheStructures(Chunk chunk) {
        this.delegate().cacheStructures(chunk);
	}

	@Override
	public void close() throws IOException {
        this.delegate().close();
	}

	@Override
	public String asString() {
		return this.delegate().asString();
	}

	@Override
	public boolean isChunkLoaded(long chunkPos) {
		return this.delegate().isChunkLoaded(chunkPos);
	}

	@Override
	public boolean shouldTickEntityAt(BlockPos pos) {
		return this.delegate().shouldTickEntityAt(pos);
	}

	@Override
	public boolean shouldTickBlockAt(BlockPos pos) {
		return this.delegate().shouldTickBlockAt(pos);
	}

	@Override
	public boolean shouldTickChunkAt(ChunkPos pos) {
		return this.delegate().shouldTickChunkAt(pos);
	}

	@Override
	public FeatureSet getEnabledFeatures() {
		return this.delegate().getEnabledFeatures();
	}

	@Override
	public Random getOrCreateRandom(Identifier id) {
		return this.delegate().getOrCreateRandom(id);
	}

	@Override
	public RandomSequencesState getRandomSequences() {
		return this.delegate().getRandomSequences();
	}

	@Override
	public ServerScoreboard getScoreboard() {
		return this.delegate().getScoreboard();
	}

	@Override
	public ServerChunkManager getChunkManager() {
		return this.delegate().getChunkManager();
	}

	@Override
	public WorldTickScheduler<Fluid> getFluidTickScheduler() {
		return this.delegate().getFluidTickScheduler();
	}

	@Override
	public WorldTickScheduler<Block> getBlockTickScheduler() {
		return this.delegate().getBlockTickScheduler();
	}
}
