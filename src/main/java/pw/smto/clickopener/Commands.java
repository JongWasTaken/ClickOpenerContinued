package pw.smto.clickopener;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import pw.smto.clickopener.api.ClickType;
import pw.smto.clickopener.interfaces.ArgumentChecker;

import static net.minecraft.server.command.CommandManager.literal;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.command.argument.IdentifierArgumentType.identifier;
import static net.minecraft.command.argument.IdentifierArgumentType.getIdentifier;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Commands {
	private static final int COMMAND_ERROR = 0;
	private static final SuggestionProvider<ServerCommandSource> ITEM_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(Registries.ITEM.stream().map(Registries.ITEM::getId), builder);
	private static final SuggestionProvider<ServerCommandSource> ITEM_TAG_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(Registries.ITEM.streamTags().map(x -> x.getTag().id()), builder);
	private static final SuggestionProvider<ServerCommandSource> BLOCK_TAG_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.streamTags().map(x -> x.getTag().id()), builder);
	private static final SuggestionProvider<ServerCommandSource> WHITELIST_ITEM_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(ClickOpenerMod.CONFIG.getItemList(), builder);
	private static final SuggestionProvider<ServerCommandSource> WHITELIST_ITEMTAG_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(ClickOpenerMod.CONFIG.getItemTagsList().stream().map(TagKey::id), builder);
	private static final SuggestionProvider<ServerCommandSource> WHITELIST_BLOCKTAG_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(ClickOpenerMod.CONFIG.getBlockTagsList().stream().map(TagKey::id), builder);
	private static final SuggestionProvider<ServerCommandSource> BLACKLIST_SUGGESTIONS = (context, builder) -> CommandSource.suggestIdentifiers(ClickOpenerMod.CONFIG.getBlacklist(), builder);
	private static final SuggestionProvider<ServerCommandSource> CLICK_TYPE_SUGGESTIONS = (context, builder) -> CommandSource.suggestMatching(Arrays.stream(ClickType.values()).map(Enum::name), builder);
	private static final String ID = "id";
	private static final String CLICK_TYPE = "clickType";
	private static final String DEFAULT_CLICK_TYPE = "defaultClickType";

	private Commands() {}

	@SuppressWarnings({"java:S1172", "unused"})
	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		var serverRoot = literal(ClickOpenerMod.MODID)
				.requires(s->s.hasPermissionLevel(4));

		var reload = literal("reload")
				.executes(Commands::reload);

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			dispatcher.register(literal("cofly")
					.executes((CommandContext<ServerCommandSource> context) -> {
						Objects.requireNonNull(context.getSource().getPlayer()).getAbilities().allowFlying = true;
						context.getSource().getPlayer().sendAbilitiesUpdate();
						return 0;
					}));
		}

		var add = literal("add")
				.then(literal("item")
						.then(argument(Commands.ID, identifier())
								.suggests(Commands.ITEM_SUGGESTIONS)
								.executes(c -> Commands.addItem(c, true)))
						.executes(c -> Commands.addItem(c, true)))
				.then(literal("blocktag")
						.then(argument(Commands.ID, identifier())
								.suggests(Commands.BLOCK_TAG_SUGGESTIONS)
								.executes(Commands::addBlockTagToWhitelist)))
				.then(literal("itemtag")
						.then(argument(Commands.ID, identifier())
								.suggests(Commands.ITEM_TAG_SUGGESTIONS)
								.executes(Commands::addItemTagToWhitelist)));

		var remove = literal("remove")
				.then(literal("item")
						.then(argument(Commands.ID, identifier())
								.suggests(Commands.WHITELIST_ITEM_SUGGESTIONS)
								.executes(c -> Commands.removeItem(c, true)))
						.executes(c -> Commands.removeItem(c, true)))
				.then(literal("blocktag")
						.then(argument(Commands.ID, identifier())
								.suggests(Commands.WHITELIST_BLOCKTAG_SUGGESTIONS)
								.executes(Commands::removeBlockTagFromWhitelist)))
				.then(literal("itemtag")
						.then(argument(Commands.ID, identifier())
								.suggests(Commands.WHITELIST_ITEMTAG_SUGGESTIONS)
								.executes(Commands::removeItemTagFromWhitelist)));

		var whitelist = literal("whitelist")
				.then(add)
				.then(remove)
				.executes(c -> Commands.displayList(c, true));

		add = literal("add")
				.then(argument(Commands.ID, identifier())
						.suggests(Commands.ITEM_SUGGESTIONS)
						.executes(c -> Commands.addItem(c, false)))
				.executes(c -> Commands.addItem(c, false));

		remove = literal("remove")
				.then(argument(Commands.ID, identifier())
						.suggests(Commands.BLACKLIST_SUGGESTIONS)
						.executes(c -> Commands.removeItem(c, false)))
				.executes(c -> Commands.removeItem(c, false));

		var blacklist = literal("blacklist")
				.then(add)
				.then(remove)
				.executes(c -> Commands.displayList(c, false));

		var defaultClickType = literal(Commands.DEFAULT_CLICK_TYPE)
				.then(argument(Commands.DEFAULT_CLICK_TYPE, word())
						.suggests(Commands.CLICK_TYPE_SUGGESTIONS)
						.executes(c -> Commands.setClickType(c, false)))
				.executes(c -> Commands.displayClickType(c, false));

		serverRoot
		.then(reload)
		.then(whitelist)
		.then(blacklist)
		.then(defaultClickType);

		var clickType = literal(Commands.CLICK_TYPE)
				.then(argument(Commands.CLICK_TYPE, word())
						.suggests(Commands.CLICK_TYPE_SUGGESTIONS)
						.executes(c -> Commands.setClickType(c, true)))
				.executes(c -> Commands.displayClickType(c, true));

		var playerRoot = literal(ClickOpenerMod.MODID+"_player")
				.then(clickType);

		dispatcher.register(serverRoot);
		dispatcher.register(playerRoot);
	}

	private static int setClickType(CommandContext<ServerCommandSource> context, boolean player) throws CommandSyntaxException {
		var type = ClickType.tryValueOf(getString(context, player ? Commands.CLICK_TYPE : Commands.DEFAULT_CLICK_TYPE));
		if (player) {
			ClickOpenerMod.PLAYER_CONFIGS.setClickType(context.getSource().getPlayerOrThrow(), type);
			context.getSource().sendFeedback(() -> Text.of("ClickType set to "+type), false);
		} else {
			ClickOpenerMod.CONFIG.setClickType(type);
			context.getSource().sendFeedback(() -> Text.of("Default ClickType set to "+type), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int reload(CommandContext<ServerCommandSource> context) {
		ClickOpenerMod.CONFIG.reload();
		ClickOpenerMod.PLAYER_CONFIGS.reload();
		context.getSource().sendFeedback(() -> Text.of("ClickOpener Config Reloaded"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int addItem(CommandContext<ServerCommandSource> context, boolean isWhitelist) throws CommandSyntaxException {
		var item = ArgumentChecker.hasArgument(context, Commands.ID) ? getIdentifier(context, Commands.ID) : Registries.ITEM.getId(context.getSource().getPlayerOrThrow().getMainHandStack().getItem());
		if (item.equals(Registries.ITEM.getDefaultId())) {
			context.getSource().sendError(Text.of("Invalid Item"));
			return Commands.COMMAND_ERROR;
		}

		ClickOpenerMod.CONFIG.addItem(item, isWhitelist);
		context.getSource().sendFeedback(() -> Text.of(item+" added to "+(isWhitelist ? "whitelist." : "blacklist.")), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int addBlockTagToWhitelist(CommandContext<ServerCommandSource> context) {
		var tag = getIdentifier(context, Commands.ID);
		ClickOpenerMod.CONFIG.addBlockTag(tag);
		context.getSource().sendFeedback(() -> Text.of("#"+tag+" added to whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int addItemTagToWhitelist(CommandContext<ServerCommandSource> context) {
		var tag = getIdentifier(context, Commands.ID);
		ClickOpenerMod.CONFIG.addItemTag(tag);
		context.getSource().sendFeedback(() -> Text.of("#"+tag+" added to whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int removeItem(CommandContext<ServerCommandSource> context, boolean isWhitelist) throws CommandSyntaxException {
		var item = ArgumentChecker.hasArgument(context, Commands.ID) ? getIdentifier(context, Commands.ID) : Registries.ITEM.getId(context.getSource().getPlayerOrThrow().getMainHandStack().getItem());
		ClickOpenerMod.CONFIG.removeItem(item, isWhitelist);
		context.getSource().sendFeedback(() -> Text.of(item+" removed from "+(isWhitelist ? "whitelist." : "blacklist.")), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int removeBlockTagFromWhitelist(CommandContext<ServerCommandSource> context) {
		var tag = getIdentifier(context, Commands.ID);
		ClickOpenerMod.CONFIG.removeBlockTag(tag);
		context.getSource().sendFeedback(() -> Text.of("#"+tag+" removed from whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int removeItemTagFromWhitelist(CommandContext<ServerCommandSource> context) {
		var tag = getIdentifier(context, Commands.ID);
		ClickOpenerMod.CONFIG.removeItemTag(tag);
		context.getSource().sendFeedback(() -> Text.of("#"+tag+" removed from whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int displayList(CommandContext<ServerCommandSource> context, boolean isWhitelist) {
		var builder = ClickOpenerMod.CONFIG.asBuilder();
		var list = (isWhitelist ? builder.whitelist() : builder.blacklist()).stream().map(Object::toString).collect(Collectors.joining("\n"));
		var header = (isWhitelist ? Text.literal("Whitelist").styled(s -> s.withColor(Formatting.GREEN)) : Text.literal("Blacklist").styled(s -> s.withColor(Formatting.RED))).append(":\n").styled(s -> s.withBold(true));
		context.getSource().sendFeedback(() -> Text.empty().append(header).append(list), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int displayClickType(CommandContext<ServerCommandSource> context, boolean player) throws CommandSyntaxException {
		ClickType clickType;
		String header;
		if (player) {
			clickType = ClickOpenerMod.PLAYER_CONFIGS.getClickType(context.getSource().getPlayerOrThrow());
			header = "ClickType";
		} else {
			clickType = ClickOpenerMod.CONFIG.getClickType();
			header = "Default ClickType";
		}
		context.getSource().sendFeedback(() -> Text.empty().append(Text.literal(header).append(":\n").styled(s -> s.withBold(true))).append(clickType.name()), false);
		return Command.SINGLE_SUCCESS;
	}
}
