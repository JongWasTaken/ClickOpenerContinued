package pw.smto.clickopener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import pw.smto.clickopener.api.ClickType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerConfigs {
	public static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(ClickOpenerMod.MODID+"_player.json");

	private final Map<UUID, PlayerConfig> configs;

	public PlayerConfigs() {
		this.configs = new HashMap<>();
	}

	private PlayerConfig getOrCreate(UUID uuid) {
		return this.configs.computeIfAbsent(uuid, id -> PlayerConfig.defaultConfig());
	}

	private void store(UUID uuid, PlayerConfig config) {
        this.configs.put(uuid, config);
        this.write();
	}

	private void modifyPlayerConfig(UUID uuid, UnaryOperator<PlayerConfig> modifyFunc) {
        this.store(uuid, modifyFunc.apply(this.getOrCreate(uuid)));
	}

	public boolean isClickTypeAllowed(ServerPlayerEntity player, ClickType clickType) {
		return clickType == null || clickType == this.getClickType(player);
	}

	public void setClickType(ServerPlayerEntity player, ClickType clickType) {
        this.modifyPlayerConfig(player.getUuid(), c -> c.withClickType(clickType));
	}

	public ClickType getClickType(ServerPlayerEntity player) {
		return this.getOrCreate(player.getUuid()).clickType();
	}

	public void reload() {
		if (Files.exists(PlayerConfigs.CONFIG_FILE)) {
			if (!this.read()) {
				//Don't write. Allow the user a chance to recover.
				return;
			}
		} else {
            this.configs.clear();
		}
        this.write();
	}

	public boolean read() {
		try (var reader = Files.newBufferedReader(PlayerConfigs.CONFIG_FILE)) {
			Map<UUID, PlayerConfig> readIn = ClickOpenerMod.GSON.fromJson(reader, new TypeToken<HashMap<UUID, PlayerConfig>>() {}.getType());
            this.configs.clear();
            this.configs.putAll(readIn);
			return true;
		} catch (IOException | JsonParseException e) {
			ClickOpenerMod.LOGGER.error("Failed to read configuration file: {}", e.getMessage());
		}
		return false;
	}

	public void write() {
		try (var out = Files.newBufferedWriter(PlayerConfigs.CONFIG_FILE)) {
			ClickOpenerMod.GSON.toJson(this.configs, out);
		} catch (IOException | JsonIOException e) {
			ClickOpenerMod.LOGGER.error("Failed to write configuration file: {}", e.getMessage());
		}
	}

	private record PlayerConfig(ClickType clickType) {
		public static PlayerConfig defaultConfig() {
			return new PlayerConfig(ClickOpenerMod.CONFIG.getClickType());
		}

		public PlayerConfig withClickType(ClickType clickType) {
			return new PlayerConfig(clickType);
		}
	}
}
