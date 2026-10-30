package com.upgrader.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.upgrader.config.*;
import com.upgrader.item.CrateTier;
import com.upgrader.item.ModItems;
import com.upgrader.network.Network;
import com.upgrader.network.Packets;
import com.upgrader.service.CrateOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /upgrader view <crate>                          (everybody) show contents + chances
 * /upgrader list                                  (everybody)
 * /upgrader edit <crate>                          (OP)  loot editor
 * /upgrader create <id> <model> [name...]         (OP)  new empty crate that looks like a built-in one
 * /upgrader rename <crate> <name...>              (OP)
 * /upgrader delete <crate>                        (OP)  custom crates only
 * /upgrader give <player> <crate> [count]         (OP)
 * /upgrader open <player> <crate>                 (OP)  roll + roulette without needing the item (command blocks!)
 * /upgrader reload                                (OP)
 */
public final class UpgraderCommand {
    private static final SuggestionProvider<CommandSourceStack> CRATES =
            (ctx, b) -> SharedSuggestionProvider.suggest(CrateManager.ids(), b);
    private static final SuggestionProvider<CommandSourceStack> MODELS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (CrateTier t : CrateTier.values()) ids.add(t.id);
        return SharedSuggestionProvider.suggest(ids, b);
    };

    private UpgraderCommand() {}

    private static boolean op(CommandSourceStack s) {
        return s.hasPermission(2);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("upgrader")
                .then(Commands.literal("view")
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                .executes(ctx -> view(ctx))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("edit").requires(UpgraderCommand::op)
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                .executes(ctx -> edit(ctx))))
                .then(Commands.literal("create").requires(UpgraderCommand::op)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("model", StringArgumentType.word()).suggests(MODELS)
                                        .executes(ctx -> create(ctx, false))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> create(ctx, true))))))
                .then(Commands.literal("rename").requires(UpgraderCommand::op)
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> rename(ctx)))))
                .then(Commands.literal("delete").requires(UpgraderCommand::op)
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                .executes(ctx -> delete(ctx))))
                .then(Commands.literal("give").requires(UpgraderCommand::op)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .executes(ctx -> give(ctx, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("open").requires(UpgraderCommand::op)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .executes(ctx -> openFor(ctx)))))
                .then(Commands.literal("reload").requires(UpgraderCommand::op).executes(ctx -> {
                    CrateManager.loadAll();
                    DropConfig.load();
                    Messages.load();
                    UpgradeConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[Upgrader] Конфиги перезагружены (шансы в сундуках данжей применятся после /reload или перезапуска)"), true);
                    return 1;
                })));
    }

    private static CrateDef crate(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "crate").toLowerCase(Locale.ROOT);
        CrateDef def = CrateManager.def(id);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("Нет ящика '" + id + "'. Список: /upgrader list"));
        }
        return def;
    }

    private static int view(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CrateDef def = crate(ctx);
        if (def == null) return 0;
        Network.toPlayer(ctx.getSource().getPlayerOrException(), Packets.OpenPreview.of(def, false, ctx.getSource().getPlayerOrException()));
        return 1;
    }

    private static int list(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Ящики:").withStyle(ChatFormatting.GRAY), false);
        for (CrateDef d : CrateManager.defs()) {
            src.sendSuccess(() -> Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(d.display())
                    .append(Component.literal("  [" + d.id + (d.builtin ? "" : ", модель " + d.style.id) + "]")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CrateDef def = crate(ctx);
        if (def == null) return 0;
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (Auth.isAuthed(sp.getUUID())) {
            Network.toPlayer(sp, Packets.OpenEditor.of(def));
        } else {
            Network.toPlayer(sp, new Packets.OpenPassword(def.id));
        }
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, boolean withName) {
        CommandSourceStack src = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        CrateTier model = CrateTier.byId(StringArgumentType.getString(ctx, "model"));
        if (model == null) {
            src.sendFailure(Component.literal("Модель: common, mid, rare, legendary или mythic"));
            return 0;
        }
        String name = withName ? StringArgumentType.getString(ctx, "name") : id;
        String err = CrateManager.create(id, name, model);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        CrateDef def = CrateManager.def(id);
        src.sendSuccess(() -> Component.literal("Создан ящик ").append(def.display())
                .append(Component.literal(" [" + id + "], модель как у " + model.displayName
                        + ". Он пустой: /upgrader edit " + id + " — наполнить, /upgrader give @s " + id + " — получить.")
                        .withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) {
        CrateDef def = crate(ctx);
        if (def == null) return 0;
        String err = CrateManager.rename(def.id, StringArgumentType.getString(ctx, "name"));
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Переименовано."), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        CrateDef def = crate(ctx);
        if (def == null) return 0;
        String err = CrateManager.delete(def.id);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ящик '" + def.id
                + "' удалён (файл с лутом остался в config/upgrader/)."), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        CrateDef def = crate(ctx);
        if (def == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ItemStack stack = ModItems.crateStack(def, count);
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Выдано " + count + " x ").append(def.display())
                .append(" игроку " + target.getGameProfile().getName()), true);
        return 1;
    }

    private static int openFor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CrateDef def = crate(ctx);
        if (def == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        return CrateOpener.open(target, def, null) ? 1 : 0;
    }
}
