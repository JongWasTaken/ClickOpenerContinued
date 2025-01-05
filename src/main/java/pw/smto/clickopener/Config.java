package pw.smto.clickopener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;

import pw.smto.clickopener.api.ClickType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * whitelist
 * - Contains ids of items (minecraft:crafting_table), item tags prefixed by item (item#minecraft:anvil),
 *   or block tags prefixed by block (block#minecraft:shulker_boxes). Tags without prefix will add both item and block.
 * <p>
 * blacklist
 * - Contains ids of items. Useful for excluding a single item from a tag (minecraft:damaged_anvil).
 */
public class Config {
	public static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(ClickOpenerMod.MODID+".json");

	private final Set<TagKey<Item>> itemTagsList;
	private final Set<TagKey<Block>> blockTagsList;
	private final Set<Identifier> itemList;
	private final Set<Identifier> blacklist;
	private ClickType clickType;

	public Set<TagKey<Item>> getItemTagsList() {
		return this.itemTagsList;
	}

	public Set<TagKey<Block>> getBlockTagsList() {
		return this.blockTagsList;
	}

	public Set<Identifier> getItemList() {
		return this.itemList;
	}

	public Set<Identifier> getBlacklist() {
		return this.blacklist;
	}

	public ClickType getClickType() {
		return this.clickType;
	}

	public Config() {
		this.itemTagsList = new HashSet<>();
		this.blockTagsList = new HashSet<>();
		this.itemList = new HashSet<>();
		this.blacklist = new HashSet<>();
		this.clickType = ClickType.RIGHT;
	}

	public void reset() {
		this.itemList.clear();
		this.itemTagsList.clear();
		this.blockTagsList.clear();
		this.blacklist.clear();
		this.clickType = ClickType.RIGHT;
	}

	public void reload() {
		if (Files.exists(Config.CONFIG_FILE)) {
			if (!this.read()) {
				//Don't write. Allow the user a chance to recover.
				return;
			}
		} else {
            this.reset();
		}
        this.write();
	}

	public boolean read() {
		try (var reader = Files.newBufferedReader(Config.CONFIG_FILE)) {
			ClickOpenerMod.GSON.fromJson(reader, ConfigBuilder.class).fill(this);
			return true;
		} catch (IOException | JsonParseException e) {
			ClickOpenerMod.LOGGER.error("Failed to read configuration file: {}", e.getMessage());
		}
		return false;
	}

	public void write() {
		var builder = this.asBuilder();
		try (var out = Files.newBufferedWriter(Config.CONFIG_FILE)) {
			ClickOpenerMod.GSON.toJson(builder, out);
		} catch (IOException | JsonIOException e) {
			ClickOpenerMod.LOGGER.error("Failed to write configuration file: {}", e.getMessage());
			ClickOpenerMod.LOGGER.error("Current Contents: {}", builder);
		}
	}

	public void addItem(Identifier id, boolean allow, boolean writeToFile) {
		if (allow) {
            this.itemList.add(id);
		} else {
            this.blacklist.add(id);
		}
		if (writeToFile) this.write();
	}

	public void addItem(Identifier id, boolean allow) {
        this.addItem(id, allow, true);
	}

	public void removeItem(Identifier id, boolean allow) {
		if (allow) {
            this.itemList.remove(id);
		} else {
            this.blacklist.remove(id);
		}
        this.write();
	}

	public void addItemTag(Identifier tag) {
        this.itemTagsList.add(TagKey.of(RegistryKeys.ITEM, tag));
        this.write();
	}

	public void addBlockTag(Identifier tag) {
        this.blockTagsList.add(TagKey.of(RegistryKeys.BLOCK, tag));
        this.write();
	}

	public void removeItemTag(Identifier tag) {
        this.itemTagsList.remove(TagKey.of(RegistryKeys.ITEM, tag));
        this.write();
	}

	public void removeBlockTag(Identifier tag) {
        this.blockTagsList.remove(TagKey.of(RegistryKeys.BLOCK, tag));
        this.write();
	}

	public void setClickType(ClickType clickType) {
		this.clickType = Objects.requireNonNullElse(clickType, ClickType.RIGHT);
	}

	public boolean isAllowed(Item item) {
		var id = Registries.ITEM.getId(item);
		return (this.itemList.contains(id)
				|| this.anyMatch(this.itemTagsList, Registries.ITEM.getEntry(item))
				|| item instanceof BlockItem bi && this.anyMatch(this.blockTagsList, Registries.BLOCK.getEntry(bi.getBlock()))
				) && !this.blacklist.contains(id);
	}

	private <T> boolean anyMatch(Set<TagKey<T>> tags, RegistryEntry<T> entry) {
		return tags.stream().anyMatch(entry::isIn);
	}

	public ConfigBuilder asBuilder() {
		return new ConfigBuilder(this);
	}

	public record ConfigBuilder(Set<String> whitelist, Set<Identifier> blacklist, ClickType defaultClickType) {
		public ConfigBuilder(Config config) {
			this(new HashSet<>(), new HashSet<>(), config.clickType);
			for (var k : config.itemTagsList) {
                this.whitelist.add("item#"+k.id());
			}
			for (var k : config.blockTagsList) {
                this.whitelist.add("block#"+k.id());
			}
			for (var b : config.itemList) {
                this.whitelist.add(b.toString());
			}
            this.blacklist.addAll(config.blacklist);
		}

		public void fill(Config config) {
			config.reset();
			for (var s : this.whitelist) {
				var arr = s.split("#",2);
				if (arr.length == 1) {
					config.itemList.add(Identifier.of(s));
				} else if (arr.length == 2) {
					var id = Identifier.of(arr[1]);
					if (!arr[0].equals("item")) {
						config.blockTagsList.add(TagKey.of(RegistryKeys.BLOCK, id));
					}
					if (!arr[0].equals("block")) {
						config.itemTagsList.add(TagKey.of(RegistryKeys.ITEM, id));
					}
				}
			}

			config.blacklist.addAll(this.blacklist);
			config.setClickType(this.defaultClickType);
		}

		@Override
		public String toString() {
			return "[whitelist="+ this.whitelist +", blacklist="+ this.blacklist +", defaultClickType=" + this.defaultClickType + "]";
		}
	}
}
