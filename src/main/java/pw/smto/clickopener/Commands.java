package pw.smto.clickopener;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import pw.smto.clickopener.api.ClickType;
import pw.smto.clickopener.interfaces.ArgumentChecker;

import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.arguments.IdentifierArgument.id;
import static net.minecraft.commands.arguments.IdentifierArgument.getId;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public class Commands {
	private static final int COMMAND_ERROR = 0;
	private static final SuggestionProvider<CommandSourceStack> ITEM_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.stream().map(BuiltInRegistries.ITEM::getKey), builder);
	private static final SuggestionProvider<CommandSourceStack> ITEM_TAG_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.getTags().map(x -> x.key().location()), builder);
	private static final SuggestionProvider<CommandSourceStack> BLOCK_TAG_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.getTags().map(x -> x.key().location()), builder);
	private static final SuggestionProvider<CommandSourceStack> WHITELIST_ITEM_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggestResource(ClickOpenerMod.CONFIG.getItemList(), builder);
	private static final SuggestionProvider<CommandSourceStack> WHITELIST_ITEMTAG_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggestResource(ClickOpenerMod.CONFIG.getItemTagsList().stream().map(TagKey::location), builder);
	private static final SuggestionProvider<CommandSourceStack> WHITELIST_BLOCKTAG_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggestResource(ClickOpenerMod.CONFIG.getBlockTagsList().stream().map(TagKey::location), builder);
	private static final SuggestionProvider<CommandSourceStack> BLACKLIST_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggestResource(ClickOpenerMod.CONFIG.getBlacklist(), builder);
	private static final SuggestionProvider<CommandSourceStack> CLICK_TYPE_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ClickType.values()).map(Enum::name), builder);
	private static final SuggestionProvider<CommandSourceStack> ALLOW_USAGE_IN_CHEST_SCREEN_SUGGESTIONS = (context, builder) -> BoolArgumentType.bool().listSuggestions(context, builder);
	private static final String ID = "id";
	private static final String CLICK_TYPE = "clickType";
	private static final String DEFAULT_CLICK_TYPE = "defaultClickType";
	private static final String ALLOW_USAGE_IN_CHEST_SCREEN = "allowUsageInChestScreen";

	private Commands() {}

	@SuppressWarnings({"java:S1172", "unused"})
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, net.minecraft.commands.Commands.CommandSelection environment) {
		var serverRoot = literal(ClickOpenerMod.MODID)
				.requires(s->s.permissions().hasPermission(Permissions.COMMANDS_ADMIN));

		var reload = literal("reload")
				.executes(Commands::reload);

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			dispatcher.register(literal("cofly")
					.executes((CommandContext<CommandSourceStack> context) -> {
						Objects.requireNonNull(context.getSource().getPlayer()).getAbilities().mayfly = true;
						context.getSource().getPlayer().onUpdateAbilities();
						return 0;
					}));
		}

		var add = literal("add")
				.then(literal("item")
						.then(argument(Commands.ID, id())
								.suggests(Commands.ITEM_SUGGESTIONS)
								.executes(c -> Commands.addItem(c, true)))
						.executes(c -> Commands.addItem(c, true)))
				.then(literal("blocktag")
						.then(argument(Commands.ID, id())
								.suggests(Commands.BLOCK_TAG_SUGGESTIONS)
								.executes(Commands::addBlockTagToWhitelist)))
				.then(literal("itemtag")
						.then(argument(Commands.ID, id())
								.suggests(Commands.ITEM_TAG_SUGGESTIONS)
								.executes(Commands::addItemTagToWhitelist)));

		var remove = literal("remove")
				.then(literal("item")
						.then(argument(Commands.ID, id())
								.suggests(Commands.WHITELIST_ITEM_SUGGESTIONS)
								.executes(c -> Commands.removeItem(c, true)))
						.executes(c -> Commands.removeItem(c, true)))
				.then(literal("blocktag")
						.then(argument(Commands.ID, id())
								.suggests(Commands.WHITELIST_BLOCKTAG_SUGGESTIONS)
								.executes(Commands::removeBlockTagFromWhitelist)))
				.then(literal("itemtag")
						.then(argument(Commands.ID, id())
								.suggests(Commands.WHITELIST_ITEMTAG_SUGGESTIONS)
								.executes(Commands::removeItemTagFromWhitelist)));

		var whitelist = literal("whitelist")
				.then(add)
				.then(remove)
				.executes(c -> Commands.displayList(c, true));

		add = literal("add")
				.then(argument(Commands.ID, id())
						.suggests(Commands.ITEM_SUGGESTIONS)
						.executes(c -> Commands.addItem(c, false)))
				.executes(c -> Commands.addItem(c, false));

		remove = literal("remove")
				.then(argument(Commands.ID, id())
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

		/*
		var allowUsageInChestScreen = literal(Commands.ALLOW_USAGE_IN_CHEST_SCREEN)
				.then(argument(Commands.ALLOW_USAGE_IN_CHEST_SCREEN, BoolArgumentType.bool())
						.suggests(Commands.ALLOW_USAGE_IN_CHEST_SCREEN_SUGGESTIONS)
						.executes(c -> ))
				.executes(c -> );

		 */

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

	private static int setClickType(CommandContext<CommandSourceStack> context, boolean player) throws CommandSyntaxException {
		var type = ClickType.tryValueOf(getString(context, player ? Commands.CLICK_TYPE : Commands.DEFAULT_CLICK_TYPE));
		if (player) {
			ClickOpenerMod.PLAYER_CONFIGS.setClickType(context.getSource().getPlayerOrException(), type);
			context.getSource().sendSuccess(() -> Component.nullToEmpty("ClickType set to "+type), false);
		} else {
			ClickOpenerMod.CONFIG.setClickType(type);
			context.getSource().sendSuccess(() -> Component.nullToEmpty("Default ClickType set to "+type), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		ClickOpenerMod.CONFIG.reload();
		ClickOpenerMod.PLAYER_CONFIGS.reload();
		context.getSource().sendSuccess(() -> Component.nullToEmpty("ClickOpener Config Reloaded"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int addItem(CommandContext<CommandSourceStack> context, boolean isWhitelist) throws CommandSyntaxException {
		var item = ArgumentChecker.hasArgument(context, Commands.ID) ? getId(context, Commands.ID) : BuiltInRegistries.ITEM.getKey(context.getSource().getPlayerOrException().getMainHandItem().getItem());
		if (item.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
			context.getSource().sendFailure(Component.nullToEmpty("Invalid Item"));
			return Commands.COMMAND_ERROR;
		}

		ClickOpenerMod.CONFIG.addItem(item, isWhitelist);
		context.getSource().sendSuccess(() -> Component.nullToEmpty(item+" added to "+(isWhitelist ? "whitelist." : "blacklist.")), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int addBlockTagToWhitelist(CommandContext<CommandSourceStack> context) {
		var tag = getId(context, Commands.ID);
		ClickOpenerMod.CONFIG.addBlockTag(tag);
		context.getSource().sendSuccess(() -> Component.nullToEmpty("#"+tag+" added to whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int addItemTagToWhitelist(CommandContext<CommandSourceStack> context) {
		var tag = getId(context, Commands.ID);
		ClickOpenerMod.CONFIG.addItemTag(tag);
		context.getSource().sendSuccess(() -> Component.nullToEmpty("#"+tag+" added to whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int removeItem(CommandContext<CommandSourceStack> context, boolean isWhitelist) throws CommandSyntaxException {
		var item = ArgumentChecker.hasArgument(context, Commands.ID) ? getId(context, Commands.ID) : BuiltInRegistries.ITEM.getKey(context.getSource().getPlayerOrException().getMainHandItem().getItem());
		ClickOpenerMod.CONFIG.removeItem(item, isWhitelist);
		context.getSource().sendSuccess(() -> Component.nullToEmpty(item+" removed from "+(isWhitelist ? "whitelist." : "blacklist.")), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int removeBlockTagFromWhitelist(CommandContext<CommandSourceStack> context) {
		var tag = getId(context, Commands.ID);
		ClickOpenerMod.CONFIG.removeBlockTag(tag);
		context.getSource().sendSuccess(() -> Component.nullToEmpty("#"+tag+" removed from whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int removeItemTagFromWhitelist(CommandContext<CommandSourceStack> context) {
		var tag = getId(context, Commands.ID);
		ClickOpenerMod.CONFIG.removeItemTag(tag);
		context.getSource().sendSuccess(() -> Component.nullToEmpty("#"+tag+" removed from whitelist."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int displayList(CommandContext<CommandSourceStack> context, boolean isWhitelist) {
		var builder = ClickOpenerMod.CONFIG.asBuilder();
		var list = (isWhitelist ? builder.whitelist() : builder.blacklist()).stream().map(Object::toString).collect(Collectors.joining("\n"));
		var header = (isWhitelist ? Component.literal("Whitelist").withStyle(s -> s.withColor(ChatFormatting.GREEN)) : Component.literal("Blacklist").withStyle(s -> s.withColor(ChatFormatting.RED))).append(":\n").withStyle(s -> s.withBold(true));
		context.getSource().sendSuccess(() -> Component.empty().append(header).append(list), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int displayClickType(CommandContext<CommandSourceStack> context, boolean player) throws CommandSyntaxException {
		ClickType clickType;
		String header;
		if (player) {
			clickType = ClickOpenerMod.PLAYER_CONFIGS.getClickType(context.getSource().getPlayerOrException());
			header = "ClickType";
		} else {
			clickType = ClickOpenerMod.CONFIG.getClickType();
			header = "Default ClickType";
		}
		context.getSource().sendSuccess(() -> Component.empty().append(Component.literal(header).append(":\n").withStyle(s -> s.withBold(true))).append(clickType.name()), false);
		return Command.SINGLE_SUCCESS;
	}
}
