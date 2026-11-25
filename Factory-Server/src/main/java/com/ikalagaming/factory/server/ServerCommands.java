package com.ikalagaming.factory.server;

import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.factory.save.Accounts;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.WorldClock;
import com.ikalagaming.factory.world.Block;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongUnaryOperator;

/**
 * The commands players type in chat after {@code /}, and the server console runs without it. Each
 * needs a permission node, which {@code permissions.yml} in this plugin declares. Commands run on
 * the world's tick thread.
 */
@Slf4j
public final class ServerCommands {

    /** Permission nodes. */
    public static final String HELP = "lotomation.command.help";

    public static final String GAMEMODE = "lotomation.command.gamemode";
    public static final String GAMEMODE_OTHERS = "lotomation.command.gamemode.others";
    public static final String OP = "lotomation.command.op";
    public static final String DEOP = "lotomation.command.deop";
    public static final String ACCEPT = "lotomation.command.accept";
    public static final String SAY = "lotomation.command.say";
    public static final String TIME = "lotomation.command.time";
    public static final String TICK = "lotomation.command.tick";
    public static final String SETBLOCK = "lotomation.command.setblock";

    /** What relative coordinates start with. */
    private static final String RELATIVE = "~";

    /** Nanoseconds in a millisecond, for showing times. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** What a command does. */
    @FunctionalInterface
    interface Handler {
        /**
         * Run it.
         *
         * @param sender Who ran it.
         * @param args The words after its name.
         */
        void run(@NonNull CommandSender sender, @NonNull List<String> args);
    }

    /**
     * A command.
     *
     * @param name Its name, typed after {@code /}.
     * @param permission The node needed to run it.
     * @param usage How to type it.
     * @param description What it does.
     * @param handler What runs.
     */
    record Command(
            @NonNull String name,
            @NonNull String permission,
            @NonNull String usage,
            @NonNull String description,
            @NonNull Handler handler) {}

    /** The world the commands act on. */
    private final ServerWorld world;

    /** The commands, by name. */
    private final Map<String, Command> commands = new LinkedHashMap<>();

    /**
     * The commands for a world.
     *
     * @param world The world.
     */
    ServerCommands(@NonNull ServerWorld world) {
        this.world = world;
        add(new Command("help", HELP, "/help", "List the commands you can use", this::help));
        add(
                new Command(
                        "gamemode",
                        GAMEMODE,
                        "/gamemode <survival|creative|spectator> [player]",
                        "Change a game mode",
                        this::gameMode));
        add(new Command("op", OP, "/op <player>", "Make a player an operator", this::op));
        add(
                new Command(
                        "deop",
                        DEOP,
                        "/deop <player>",
                        "Take operator away from a player",
                        this::deop));
        add(
                new Command(
                        "accept",
                        ACCEPT,
                        "/accept <player>",
                        "Let a player who asked to join in",
                        this::accept));
        add(
                new Command(
                        "say",
                        SAY,
                        "/say <message>",
                        "Speak to everyone as the server",
                        this::say));
        add(
                new Command(
                        "time",
                        TIME,
                        "/time [set <noon|sunset|HH:MM|hour> | add <ticks|hours h|days d>]",
                        "Show or change the time of day",
                        this::time));
        add(
                new Command(
                        "tick",
                        TICK,
                        "/tick [rate <ticks a second> | freeze | unfreeze | step [ticks] | sprint"
                                + " <ticks|stop>]",
                        "Show or change how fast the world runs; players keep moving",
                        this::tick));
        add(
                new Command(
                        "setblock",
                        SETBLOCK,
                        "/setblock <x> <y> <z> <block>",
                        "Change one block; ~ is relative to you",
                        this::setBlock));
    }

    /**
     * Add a command.
     *
     * @param command The command.
     */
    private void add(@NonNull Command command) {
        commands.put(command.name(), command);
    }

    /**
     * The command names, for registering with the console.
     *
     * @return The names.
     */
    public List<String> names() {
        return List.copyOf(commands.keySet());
    }

    /**
     * Run a command line. Tick thread only.
     *
     * @param sender Who runs it.
     * @param line The line, with or without the leading {@code /}.
     */
    public void dispatch(@NonNull CommandSender sender, @NonNull String line) {
        final String text = line.startsWith("/") ? line.substring(1) : line;
        final List<String> words =
                Arrays.stream(text.strip().split("\\s+")).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) {
            return;
        }
        final Command command = commands.get(words.get(0).toLowerCase(Locale.ROOT));
        if (command == null) {
            sender.reply(ChatText.of("Unknown command. Type /help for a list.", ChatText.RED));
            return;
        }
        if (!world.permissions().has(sender.subject(), command.permission())) {
            sender.reply(ChatText.of("You don't have permission to do that.", ChatText.RED));
            return;
        }
        log.info("{} ran /{}", sender.name(), text.strip());
        command.handler().run(sender, words.subList(1, words.size()));
    }

    /**
     * List the commands the sender may use.
     *
     * @param sender Who asked.
     * @param args Ignored.
     */
    private void help(@NonNull CommandSender sender, @NonNull List<String> args) {
        sender.reply(ChatText.of("Commands you can use:", ChatText.YELLOW));
        for (Command command : commands.values()) {
            if (world.permissions().has(sender.subject(), command.permission())) {
                sender.reply(
                        ChatText.of(command.usage(), ChatText.WHITE)
                                .append(" - " + command.description(), ChatText.GREY));
            }
        }
    }

    /**
     * Change the sender's or another player's game mode.
     *
     * @param sender Who asked.
     * @param args The mode, and optionally a player.
     */
    private void gameMode(@NonNull CommandSender sender, @NonNull List<String> args) {
        final Optional<GameMode> mode =
                args.isEmpty() ? Optional.empty() : GameMode.named(args.get(0));
        if (mode.isEmpty() || args.size() > 2) {
            usage(sender, "gamemode");
            return;
        }
        final ServerPlayer target;
        if (args.size() == 2) {
            if (!world.permissions().has(sender.subject(), GAMEMODE_OTHERS)) {
                sender.reply(
                        ChatText.of(
                                "You don't have permission to change others' game modes.",
                                ChatText.RED));
                return;
            }
            target = online(args.get(1));
            if (target == null) {
                sender.reply(ChatText.of(args.get(1) + " isn't playing.", ChatText.RED));
                return;
            }
        } else if (sender.player() != null) {
            target = sender.player();
        } else {
            sender.reply(ChatText.of("Name a player: /gamemode <mode> <player>", ChatText.RED));
            return;
        }
        world.setGameMode(target, mode.get());
        final String modeName = mode.get().id();
        world.tell(target, ChatText.of("Your game mode is now " + modeName + ".", ChatText.GREY));
        if (target != sender.player()) {
            sender.reply(
                    ChatText.of(
                            target.getName() + "'s game mode is now " + modeName + ".",
                            ChatText.GREY));
        }
    }

    /**
     * Make a player an operator.
     *
     * @param sender Who asked.
     * @param args The player.
     */
    private void op(@NonNull CommandSender sender, @NonNull List<String> args) {
        final Accounts.Account account = account(sender, args, "op");
        if (account == null) {
            return;
        }
        if (account.role().isOperator()) {
            sender.reply(
                    ChatText.of(account.username() + " is already an operator.", ChatText.GREY));
            return;
        }
        changeRole(account, Accounts.Role.OPERATOR);
        sender.reply(ChatText.of(account.username() + " is now an operator.", ChatText.GREY));
        final ServerPlayer player = world.player(account.id());
        if (player != null) {
            world.tell(player, ChatText.of("You are now an operator.", ChatText.YELLOW));
        }
    }

    /**
     * Take operator away from a player. The owner keeps it.
     *
     * @param sender Who asked.
     * @param args The player.
     */
    private void deop(@NonNull CommandSender sender, @NonNull List<String> args) {
        final Accounts.Account account = account(sender, args, "deop");
        if (account == null) {
            return;
        }
        if (account.role() == Accounts.Role.OWNER) {
            sender.reply(ChatText.of("The owner can't be made a player.", ChatText.RED));
            return;
        }
        if (!account.role().isOperator()) {
            sender.reply(ChatText.of(account.username() + " isn't an operator.", ChatText.GREY));
            return;
        }
        changeRole(account, Accounts.Role.PLAYER);
        sender.reply(ChatText.of(account.username() + " is no longer an operator.", ChatText.GREY));
        final ServerPlayer player = world.player(account.id());
        if (player != null) {
            world.tell(player, ChatText.of("You are no longer an operator.", ChatText.YELLOW));
        }
    }

    /**
     * Let in a player who asked to join.
     *
     * @param sender Who asked.
     * @param args The player.
     */
    private void accept(@NonNull CommandSender sender, @NonNull List<String> args) {
        final Accounts.Account account = account(sender, args, "accept");
        if (account == null) {
            return;
        }
        if (!account.pending()) {
            sender.reply(ChatText.of(account.username() + " can already join.", ChatText.GREY));
            return;
        }
        world.getAccess().accounts().update(account.withPending(false));
        sender.reply(
                ChatText.of(
                        account.username() + " can join now; they need to connect again.",
                        ChatText.GREY));
    }

    /**
     * Speak to everyone as the server.
     *
     * @param sender Who asked.
     * @param args The words.
     */
    private void say(@NonNull CommandSender sender, @NonNull List<String> args) {
        if (args.isEmpty()) {
            usage(sender, "say");
            return;
        }
        world.broadcast(ChatText.of("[Server] " + String.join(" ", args), ChatText.GOLD));
    }

    /**
     * Show the time, set the time of day keeping the day, or move the clock on or back. Moving it
     * by days changes the moon's phase; setting it doesn't.
     *
     * @param sender Who asked.
     * @param args Nothing, {@code set <time of day>} or {@code add <length>}.
     */
    private void time(@NonNull CommandSender sender, @NonNull List<String> args) {
        final long now = world.getTime();
        if (args.isEmpty()) {
            sender.reply(ChatText.of(TimeArguments.describe(now), ChatText.GREY));
            return;
        }
        if (args.size() != 2) {
            usage(sender, "time");
            return;
        }
        final OptionalLong changed =
                switch (args.get(0).toLowerCase(Locale.ROOT)) {
                    case "set" ->
                            mapped(
                                    TimeArguments.timeOfDay(args.get(1)),
                                    ofDay -> TimeArguments.sameDay(now, ofDay));
                    case "add" -> mapped(TimeArguments.length(args.get(1)), step -> now + step);
                    default -> OptionalLong.empty();
                };
        if (changed.isEmpty()) {
            usage(sender, "time");
            return;
        }
        final long time = Math.max(0, changed.getAsLong());
        world.setTime(time);
        world.broadcast(
                ChatText.of(
                        sender.name() + " set the time to " + TimeArguments.describe(time) + ".",
                        ChatText.GREY));
    }

    /**
     * Show how fast the world runs, or change it. Players keep moving whatever the world does. Only
     * single player may run it faster than normal.
     *
     * @param sender Who asked.
     * @param args Nothing, {@code rate <n>}, {@code freeze}, {@code unfreeze}, {@code step [n]} or
     *     {@code sprint <n|stop>}.
     */
    private void tick(@NonNull CommandSender sender, @NonNull List<String> args) {
        final WorldClock clock = world.clock();
        if (args.isEmpty()) {
            sender.reply(ChatText.of(describe(clock), ChatText.GREY));
            return;
        }
        final String action = args.get(0).toLowerCase(Locale.ROOT);
        final String what;
        WorldClock changed;
        switch (action) {
            case "rate" -> {
                final OptionalLong rate =
                        args.size() == 2 ? whole(args.get(1)) : OptionalLong.empty();
                if (rate.isEmpty()
                        || rate.getAsLong() < 1
                        || rate.getAsLong() > WorldClock.MAX_RATE) {
                    usage(sender, "tick");
                    return;
                }
                if (rate.getAsLong() > WorldClock.NORMAL_RATE && !world.isSpeedUpAllowed()) {
                    sender.reply(
                            ChatText.of(
                                    "The world can only run faster than normal in single player.",
                                    ChatText.RED));
                    return;
                }
                changed = clock.withRate((int) rate.getAsLong());
                what = "set the world to " + rate.getAsLong() + " ticks a second";
            }
            case "freeze" -> {
                changed = clock.withFrozen(true);
                what = "froze the world";
            }
            case "unfreeze" -> {
                changed = clock.withFrozen(false);
                what = "unfroze the world";
            }
            case "step" -> {
                final OptionalLong ticks =
                        args.size() == 1 ? OptionalLong.of(1) : whole(args.get(1));
                if (args.size() > 2 || ticks.isEmpty() || ticks.getAsLong() < 1) {
                    usage(sender, "tick");
                    return;
                }
                changed = clock.stepping(ticks.getAsLong());
                what = "stepped the world " + ticks.getAsLong() + " ticks";
            }
            case "sprint" -> {
                if (args.size() != 2) {
                    usage(sender, "tick");
                    return;
                }
                if ("stop".equalsIgnoreCase(args.get(1))) {
                    changed = clock.sprinting(0);
                    what = "stopped the world sprinting";
                } else {
                    final OptionalLong ticks = whole(args.get(1));
                    if (ticks.isEmpty() || ticks.getAsLong() < 1) {
                        usage(sender, "tick");
                        return;
                    }
                    if (!world.isSpeedUpAllowed()) {
                        sender.reply(
                                ChatText.of(
                                        "The world can only sprint in single player.",
                                        ChatText.RED));
                        return;
                    }
                    changed = clock.sprinting(ticks.getAsLong());
                    what = "set the world sprinting through " + ticks.getAsLong() + " ticks";
                }
            }
            default -> {
                usage(sender, "tick");
                return;
            }
        }
        world.setClock(changed);
        world.broadcast(ChatText.of(sender.name() + " " + what + ".", ChatText.GREY));
    }

    /**
     * How fast the world runs and how far it has run, for {@code /tick}.
     *
     * @param clock The clock.
     * @return The text.
     */
    private String describe(@NonNull WorldClock clock) {
        final String state;
        if (clock.isSprinting()) {
            state = "sprinting, " + clock.sprintTicks() + " ticks to go";
        } else if (clock.frozen()) {
            state =
                    clock.stepTicks() > 0
                            ? "frozen, stepping " + clock.stepTicks() + " more ticks"
                            : "frozen";
        } else {
            state = clock.rate() + " ticks a second";
        }
        return String.format(
                Locale.ROOT,
                "World: %s; %d world frames, %d ticks; the last world tick took %.2f ms.",
                state,
                clock.worldFrames(),
                clock.worldTicks(),
                world.getWorldTickNanos() / NANOS_PER_MILLI);
    }

    /**
     * Change one block, which must be in a loaded chunk.
     *
     * @param sender Who asked.
     * @param args The x, y and z, each a whole number or {@code ~} with an optional offset from the
     *     sender, then the block.
     */
    private void setBlock(@NonNull CommandSender sender, @NonNull List<String> args) {
        if (args.size() != 4) {
            usage(sender, "setblock");
            return;
        }
        final ServerPlayer player = sender.player();
        final OptionalLong x = coordinate(args.get(0), player == null ? null : player.getX());
        final OptionalLong y = coordinate(args.get(1), player == null ? null : player.getY());
        final OptionalLong z = coordinate(args.get(2), player == null ? null : player.getZ());
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            usage(sender, "setblock");
            return;
        }
        final Optional<String> block = world.blockNamed(args.get(3));
        if (block.isEmpty()) {
            sender.reply(ChatText.of("There's no block named " + args.get(3) + ".", ChatText.RED));
            return;
        }
        if (!world.setBlock(
                (int) x.getAsLong(),
                (int) y.getAsLong(),
                (int) z.getAsLong(),
                new Block(block.get(), null))) {
            sender.reply(ChatText.of("That block isn't loaded.", ChatText.RED));
            return;
        }
        sender.reply(
                ChatText.of(
                        String.format(
                                Locale.ROOT,
                                "Set %d, %d, %d to %s.",
                                x.getAsLong(),
                                y.getAsLong(),
                                z.getAsLong(),
                                block.get()),
                        ChatText.GREY));
    }

    /**
     * A block coordinate: a whole number, or {@code ~} and an optional whole offset from where the
     * sender stands.
     *
     * @param text What was typed.
     * @param here Where the sender is along the axis, or null for the console.
     * @return The coordinate, or empty if it isn't one.
     */
    static OptionalLong coordinate(@NonNull String text, Double here) {
        if (!text.startsWith(RELATIVE)) {
            return whole(text);
        }
        if (here == null) {
            return OptionalLong.empty();
        }
        final String offset = text.substring(RELATIVE.length());
        final OptionalLong by = offset.isEmpty() ? OptionalLong.of(0) : whole(offset);
        return by.isEmpty() ? by : OptionalLong.of((long) Math.floor(here) + by.getAsLong());
    }

    /**
     * A whole number within the range of an {@code int}.
     *
     * @param text What was typed.
     * @return The number, or empty if it isn't one.
     */
    static OptionalLong whole(@NonNull String text) {
        try {
            return OptionalLong.of(Integer.parseInt(text.strip()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Change an optional time.
     *
     * @param value The time, or empty.
     * @param change What to make of it.
     * @return The changed time, or empty.
     */
    private static OptionalLong mapped(
            @NonNull OptionalLong value, @NonNull LongUnaryOperator change) {
        return value.isPresent() ? OptionalLong.of(change.applyAsLong(value.getAsLong())) : value;
    }

    /**
     * The account a command names, or tell the sender why there isn't one.
     *
     * @param sender Who asked.
     * @param args The words, with the name first.
     * @param command The command, for its usage.
     * @return The account, or null.
     */
    private Accounts.Account account(
            @NonNull CommandSender sender, @NonNull List<String> args, @NonNull String command) {
        if (args.size() != 1) {
            usage(sender, command);
            return null;
        }
        final Optional<Accounts.Account> account = world.getAccess().accounts().byName(args.get(0));
        if (account.isEmpty()) {
            sender.reply(
                    ChatText.of(
                            "No player named " + args.get(0) + " has been here.", ChatText.RED));
            return null;
        }
        return account.get();
    }

    /**
     * Change an account's role, and the player's if they are here.
     *
     * @param account The account.
     * @param role The new role.
     */
    private void changeRole(@NonNull Accounts.Account account, @NonNull Accounts.Role role) {
        final Accounts.Account changed = account.withRole(role);
        world.getAccess().accounts().update(changed);
        final ServerPlayer player = world.player(account.id());
        if (player != null) {
            player.setAccount(changed);
        }
    }

    /**
     * The player with a name who is in the world.
     *
     * @param name The name, in any case.
     * @return The player, or null.
     */
    private ServerPlayer online(@NonNull String name) {
        for (ServerPlayer player : new ArrayList<>(world.players())) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Tell the sender how to type a command.
     *
     * @param sender Who asked.
     * @param command The command.
     */
    private void usage(@NonNull CommandSender sender, @NonNull String command) {
        sender.reply(ChatText.of("Usage: " + commands.get(command).usage(), ChatText.RED));
    }
}
