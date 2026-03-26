package pw.smto.clickopener.api;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

public class OpenerRegistry {
	private static final Map<Item, Opener<?, ?>> OPENERS = new HashMap<>();

	private OpenerRegistry() {}

	/**
	 * If in singleplayer, this only loads for the first server.
	 */
	@SuppressWarnings("unused")
	public static void onServerLoading(MinecraftServer server) {
		if (OpenerRegistry.OPENERS.isEmpty()) {
			OpenerRegisterEvent.EVENT.invoker().onRegister(OpenerRegistry::register);
		}
	}

	private static void register(Item item, Opener<?, ?> opener) {
        OpenerRegistry.OPENERS.put(item, opener);
	}

	public static Opener<?, ?> get(Item item) {
		return OpenerRegistry.OPENERS.get(item);
	}
}
