import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class EnchantedVeinminerTest {
    static MinecraftServer server;
    static ServerLevel level;
    static ServerPlayer player;
    static EmbeddedChannel channel;
    static int passed, failed;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        server = boot();
        long deadline = System.currentTimeMillis() + 180_000;
        while (!server.isReady()) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never became ready");
            Thread.sleep(50);
        }
        ticks(20);
        level = server.overworld();
        try {
            if (args.length >= 2 && args[0].equals("explore")) {
                explore(Path.of(args[1]));
            } else {
                Scenarios.run();
                System.out.println("SUMMARY " + passed + " passed, " + failed + " failed");
                for (String f : failures) System.out.println("  - " + f);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            failed++;
        } finally {
            on(() -> { server.halt(false); return null; });
            Thread.sleep(3000);
            System.exit(failed == 0 ? 0 : 1);
        }
    }

    /** Vanilla by default; -Dharness.main=<class> boots a plugin platform in-process instead (PLATFORM= in run.sh). */
    static MinecraftServer boot() throws Exception {
        String main = System.getProperty("harness.main");
        if (main == null) {
            net.minecraft.server.Main.main(new String[] {"--nogui"});
            return findServer();
        }
        Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) new String[] {"--nogui"});
        java.lang.reflect.Method get = MinecraftServer.class.getMethod("getServer");
        long deadline = System.currentTimeMillis() + 180_000;
        Object s;
        while ((s = get.invoke(null)) == null) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never started");
            Thread.sleep(50);
        }
        return (MinecraftServer) s;
    }

    @SuppressWarnings("unchecked")
    static MinecraftServer findServer() throws Exception {
        Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
        Field hooksField = hooksClass.getDeclaredField("hooks");
        hooksField.setAccessible(true);
        Map<Thread, Thread> hooks = (Map<Thread, Thread>) hooksField.get(null);
        for (Thread t : hooks.keySet()) {
            for (Field f : t.getClass().getDeclaredFields()) {
                if (MinecraftServer.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (MinecraftServer) f.get(t);
                }
            }
        }
        throw new IllegalStateException("server instance not found in shutdown hooks");
    }

    static <T> T on(Callable<T> task) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        server.submit(() -> {
            try {
                out.set(task.call());
            } catch (Throwable t) {
                err.set(t);
            }
        }).join();
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    static void ticks(int n) throws InterruptedException {
        int target = server.getTickCount() + n;
        while (server.getTickCount() < target) Thread.sleep(2);
    }

    /** Runs a console command on the server thread and returns its chat output. */
    static List<String> cmd(String command) {
        return on(() -> {
            List<String> out = new ArrayList<>();
            // a proxy, not an anonymous class: plugin platforms add methods (getBukkitSender), answered by the server
            CommandSource capture = (CommandSource) java.lang.reflect.Proxy.newProxyInstance(CommandSource.class.getClassLoader(),
                new Class<?>[] {CommandSource.class}, (proxy, m, a) -> switch (m.getName()) {
                    case "sendSystemMessage" -> { out.add(((Component) a[0]).getString()); yield null; }
                    case "acceptsSuccess", "acceptsFailure" -> true;
                    case "shouldInformAdmins" -> false;
                    default -> m.invoke(server, a);
                });
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), command);
            return out;
        });
    }

    /** A /data get source as full SNBT ("null" when missing): Paper and Purpur cut /data get output at 128 characters. */
    static String full(String source) {
        cmd("data remove storage harness:full v");
        cmd("data modify storage harness:full v set from " + source);
        return on(() -> String.valueOf(server.getCommandStorage().get(net.minecraft.resources.Identifier.parse("harness:full")).get("v")));
    }

    static void explore(Path file) throws Exception {
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("//")) continue;
            if (line.startsWith("!tick ")) { ticks(Integer.parseInt(line.substring(6).trim())); continue; }
            if (line.equals("!player")) { spawnPlayer(); continue; }
            if (line.startsWith("!sneak ")) { sneak(Boolean.parseBoolean(line.substring(7).trim())); continue; }
            if (line.equals("!chat")) { for (String m : chat()) System.out.println("[EXPLORE] chat| " + m); continue; }
            if (line.startsWith("!destroy ")) {
                String[] p = line.substring(9).trim().split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                System.out.println("[EXPLORE] destroy " + pos + " -> " + destroy(pos) + " items " + itemsNear(pos, 3));
                continue;
            }
            List<String> out = cmd(line);
            System.out.println("[EXPLORE] > " + line);
            for (String o : out) System.out.println("[EXPLORE]     " + o.replace("\n", "\n[EXPLORE]     "));
        }
    }

    static void spawnPlayer() {
        if (player != null) return;
        on(() -> {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("VeinTester".getBytes()), "VeinTester");
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer p = new ServerPlayer(server, level, profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(new ChannelHandler[] {connection});
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            player = p;
            return null;
        });
    }

    /** Drains chat/action-bar packets sent to the mock player: "text {clicks=n}". */
    static List<String> chat() {
        return on(() -> {
            List<String> out = new ArrayList<>();
            Object o;
            while ((o = channel.readOutbound()) != null) {
                if (o instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p)
                    out.add((p.overlay() ? "[actionbar] " : "") + p.content().getString().replace("\n", "⏎") + " {clicks=" + clicks(p.content()) + "}");
                else if (o instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket p)
                    out.add("[actionbar] " + p.text().getString());
            }
            return out;
        });
    }

    static int clicks(Component c) {
        int n = c.getStyle().getClickEvent() != null ? 1 : 0;
        for (Component s : c.getSiblings()) n += clicks(s);
        return n;
    }

    static void sneak(boolean on) {
        on(() -> {
            player.setShiftKeyDown(on);
            player.setPose(on ? Pose.CROUCHING : Pose.STANDING);
            return null;
        });
    }

    static boolean destroy(BlockPos pos) {
        return on(() -> player.gameMode.destroyBlock(pos));
    }

    static String blockId(BlockPos pos) {
        return on(() -> {
            BlockState s = level.getBlockState(pos);
            return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        });
    }

    static Map<String, Integer> itemsNear(BlockPos pos, double r) {
        return on(() -> {
            Map<String, Integer> m = new java.util.TreeMap<>();
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(r))) {
                ItemStack s = e.getItem();
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
            return m;
        });
    }

    static int xpNear(BlockPos pos, double r) {
        return on(() -> {
            int total = 0;
            for (ExperienceOrb o : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(r))) total += o.getValue();
            return total;
        });
    }

    static ItemStack mainhand() {
        return on(() -> player.getMainHandItem().copy());
    }

    static void setMainhand(ItemStack s) {
        on(() -> { player.setItemInHand(InteractionHand.MAIN_HAND, s.copy()); return null; });
    }

    /** Puts left and right into a fresh anvil and returns the result slot. */
    static ItemStack anvil(ItemStack left, ItemStack right) {
        return on(() -> {
            AnvilMenu m = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(level, player.blockPosition()));
            // CraftBukkit builds a Bukkit view of the menu, which needs the title that openMenu would set
            try {
                m.getClass().getMethod("setTitle", Component.class).invoke(m, Component.literal("Repair & Name"));
            } catch (NoSuchMethodException e) {
                // vanilla: no title needed
            }
            m.getSlot(0).set(left.copy());
            m.getSlot(1).set(right.copy());
            m.createResult();
            return m.getSlot(2).getItem().copy();
        });
    }

    static Holder.Reference<Enchantment> enchantment(String id) {
        return on(() -> server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow());
    }

    /** Rolls the enchanting table's selection at the given level and counts rolls that include the enchantment. */
    static int tableRolls(String itemId, String enchId, int cost, int rolls) {
        return on(() -> {
            Registry<Enchantment> reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder.Reference<Enchantment> target = reg.get(Identifier.parse(enchId)).orElseThrow();
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
            RandomSource rnd = RandomSource.create(42);
            int hits = 0;
            for (int i = 0; i < rolls; i++) {
                List<EnchantmentInstance> picked = EnchantmentHelper.selectEnchantment(rnd, stack, cost,
                    java.util.stream.StreamSupport.stream(reg.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE).spliterator(), false));
                for (EnchantmentInstance e : picked) if (e.enchantment().is(target.key())) hits++;
            }
            return hits;
        });
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        } else {
            failed++;
            failures.add(name + ": " + detail);
            System.out.println("[FAIL] " + name + "  (" + detail + ")");
        }
    }

    static void info(String msg) {
        System.out.println("[INFO] " + msg);
    }
}

class Scenarios {
    static final int OX = 111, OY = 6, OZ = 110;
    static final BlockPos O = new BlockPos(OX, OY, OZ);
    static final String ENCH = "enchanted_veinminer:veinminer";
    static final String HAS = "[minecraft:enchantments~[{enchantments:\"" + ENCH + "\"}]]";
    // on a plugin platform run.sh passes the base's and the add-on's pack ids in -Dharness.packs
    static final String[] PACKS = System.getProperty("harness.packs", "").split(" ");
    static final String BASE = PACKS.length == 2 ? PACKS[0] : baseZip(), OLD = "file/veinminer-1.0.0",
        ADDON = PACKS.length == 2 ? PACKS[1] : "file/EnchantedVeinminer-1.0.0.zip";
    static final int[][] IRON = {{0,0,0},{1,0,0},{2,0,0},{2,1,0},{1,0,1},{3,2,1}};
    static final int[][] FOUR = {{0,0,0},{1,0,0},{0,1,0},{1,1,0}};

    static List<String> cmd(String c) { return EnchantedVeinminerTest.cmd(c); }
    static List<String> chat() { return EnchantedVeinminerTest.chat(); }
    static void ticks(int n) throws InterruptedException { EnchantedVeinminerTest.ticks(n); }
    static void check(String name, boolean ok, String detail) { EnchantedVeinminerTest.check(name, ok, detail); }
    static void info(String msg) { EnchantedVeinminerTest.info(msg); }

    static BlockPos at(int dx, int dy, int dz) { return new BlockPos(OX + dx, OY + dy, OZ + dz); }

    static void arena() throws Exception {
        cmd("fill 100 0 100 131 15 131 minecraft:stone");
        cmd("fill 110 5 110 110 6 110 minecraft:air");
        cmd("kill @e[type=item]");
        cmd("kill @e[type=experience_orb]");
        cmd("gamemode survival VeinTester");
        cmd("tp VeinTester 110.5 5 110.5 -90 -10");
        EnchantedVeinminerTest.sneak(true);
        ticks(2);
    }

    static void place(String block, int[][] offs) {
        for (int[] o : offs) cmd("setblock " + (OX + o[0]) + " " + (OY + o[1]) + " " + (OZ + o[2]) + " " + block);
    }

    static void tool(String spec) { cmd("item replace entity VeinTester weapon.mainhand with " + spec); }

    static int remaining(String blockId, int[][] offs) {
        int n = 0;
        for (int[] o : offs) if (EnchantedVeinminerTest.blockId(at(o[0], o[1], o[2])).equals(blockId)) n++;
        return n;
    }

    static int count(Map<String, Integer> items, String id) { return items.getOrDefault(id, 0); }

    static String baseZip() {
        try (var s = Files.list(Path.of("world/datapacks"))) {
            return "file/" + s.map(f -> f.getFileName().toString()).filter(n -> n.startsWith("Veinminer-") && n.endsWith(".zip")).findFirst().orElseThrow();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean animating() {
        return EnchantedVeinminerTest.on(() -> {
            for (net.minecraft.world.entity.Entity e : EnchantedVeinminerTest.level.getAllEntities())
                if (e.entityTags().stream().anyMatch(g -> g.equals("veinminer.ctl") || g.equals("veinminer.fx") || g.equals("veinminer.ghost") || g.equals("veinminer.pend"))) return true;
            return false;
        });
    }

    /** Mines O, then waits for Veinminer's chain animation (1.2.0+) to deliver the held drops. */
    static void mine() throws Exception {
        EnchantedVeinminerTest.destroy(O);
        ticks(3);
        for (int i = 0; i < 80 && animating(); i++) ticks(1);
        ticks(1);
    }

    static String held() {
        ItemStack s = EnchantedVeinminerTest.mainhand();
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + " dmg=" + s.getDamageValue() + " " + s.getEnchantments();
    }

    static int damage() { return EnchantedVeinminerTest.mainhand().getDamageValue(); }

    static boolean mainhandIs(String itemPredicate) {
        return cmd("execute if items entity VeinTester weapon.mainhand " + itemPredicate).toString().contains("Test passed");
    }

    static int score(String holder) {
        String out = cmd("scoreboard players get " + holder + " enchanted_veinminer.data").toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(" has (-?\\d+) ").matcher(out);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
    }

    static int requiresCount() {
        String out = EnchantedVeinminerTest.full("storage veinminer:meta requires");
        return out.equals("null") ? -1 : out.split("Your pickaxe needs the ", -1).length - 1;
    }

    static boolean isWarning(String m) { return m.startsWith("✦ Enchanted Veinminer "); }

    static void pack(String action, String id) throws Exception {
        List<String> out = cmd("datapack " + action + " \"" + id + "\"");
        info("datapack " + action + " " + id + " -> " + out);
        ticks(5);
    }

    /** Mines the IRON vein with the given tool; returns how many of its 6 ores are left. */
    static int ironVein(String toolSpec) throws Exception {
        arena();
        place("minecraft:iron_ore", IRON);
        tool(toolSpec);
        mine();
        return remaining("minecraft:iron_ore", IRON);
    }

    static void run() throws Exception {
        EnchantedVeinminerTest.spawnPlayer();
        cmd("forceload add 96 96 143 143");
        cmd("gamemode survival VeinTester");
        ticks(80);
        pack("disable", OLD);
        List<String> packs = cmd("datapack list enabled");
        check("packs: Veinminer 1.1.0 + add-on enabled, 1.0.0 off", packs.toString().contains(BASE) && packs.toString().contains(ADDON)
            && !packs.toString().contains(OLD), packs.toString());
        chat();
        ticks(50);
        List<String> c = chat();
        check("no warning with Veinminer 1.1.0 present", score("#problem") == 0 && c.stream().noneMatch(Scenarios::isWarning),
            "problem=" + score("#problem") + " chat=" + c);
        check("enchantment registered", score("#registered") == 1, "registered=" + score("#registered"));
        check("enchantment name is Veinminer", EnchantedVeinminerTest.enchantment(ENCH).value().description().getString().equals("Veinminer"),
            EnchantedVeinminerTest.enchantment(ENCH).value().description().getString());
        check("requirement registered with Veinminer once", requiresCount() == 1, "requires=" + requiresCount());

        // 1. the gate
        int left = ironVein("minecraft:iron_pickaxe");
        Map<String, Integer> it = EnchantedVeinminerTest.itemsNear(O, 3);
        check("unenchanted pickaxe breaks a single block", left == 5 && count(it, "minecraft:raw_iron") == 1 && damage() == 1,
            "left=" + left + " items " + it + " " + held());
        List<String> bar = chat();
        check("no action bar without the enchantment", bar.stream().noneMatch(m -> m.startsWith("[actionbar]")), bar.toString());

        arena();
        place("minecraft:iron_ore", IRON);
        tool("minecraft:iron_pickaxe");
        List<String> en = cmd("enchant VeinTester " + ENCH);
        info("enchant pickaxe -> " + en);
        mine();
        it = EnchantedVeinminerTest.itemsNear(O, 1.5);
        check("/enchant works on a pickaxe", en.toString().contains("Applied enchantment"), en.toString());
        check("enchanted pickaxe veinmines", remaining("minecraft:iron_ore", IRON) == 0 && count(it, "minecraft:raw_iron") == 6 && damage() == 6,
            "left=" + remaining("minecraft:iron_ore", IRON) + " items " + it + " " + held());
        bar = chat();
        check("action bar reports 6 blocks", bar.stream().anyMatch(m -> m.startsWith("[actionbar]") && m.contains("6 blocks mined")), bar.toString());

        for (String p : new String[] {"wooden", "stone", "copper", "golden", "iron", "diamond", "netherite"}) {
            arena();
            tool("minecraft:" + p + "_pickaxe");
            en = cmd("enchant VeinTester " + ENCH);
            if (!mainhandIs("minecraft:" + p + "_pickaxe" + HAS)) check("/enchant on " + p + " pickaxe", false, en.toString());
        }
        check("/enchant works on all 7 pickaxes", true, "");

        arena();
        place("minecraft:deepslate_diamond_ore", FOUR);
        tool("minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:fortune\":3,\"" + ENCH + "\":1}]");
        mine();
        it = EnchantedVeinminerTest.itemsNear(O, 1.5);
        check("Fortune III + Veinminer: vein with fortune drops", remaining("minecraft:deepslate_diamond_ore", FOUR) == 0 && count(it, "minecraft:diamond") >= 4,
            "items " + it);

        arena();
        place("minecraft:diamond_ore", FOUR);
        tool("minecraft:iron_pickaxe[minecraft:enchantments={\"minecraft:silk_touch\":1}]");
        en = cmd("enchant VeinTester " + ENCH);
        mine();
        it = EnchantedVeinminerTest.itemsNear(O, 1.5);
        check("Silk Touch + Veinminer combine and veinmine", en.toString().contains("Applied enchantment") && count(it, "minecraft:diamond_ore") == 4,
            en + " items " + it);

        arena();
        place("minecraft:iron_ore", IRON);
        tool("minecraft:iron_pickaxe" + "[minecraft:enchantments={\"" + ENCH + "\":1}]");
        EnchantedVeinminerTest.sneak(false);
        mine();
        check("Veinminer's own rules still apply (not sneaking -> single block)", remaining("minecraft:iron_ore", IRON) == 5,
            "left=" + remaining("minecraft:iron_ore", IRON));

        // 2. not for other tools
        for (String t : new String[] {"minecraft:diamond_shovel", "minecraft:diamond_axe", "minecraft:diamond_sword", "minecraft:diamond_hoe",
                "minecraft:shears", "minecraft:mace", "minecraft:fishing_rod"}) {
            arena();
            tool(t);
            en = cmd("enchant VeinTester " + ENCH);
            check("/enchant refused on " + t, !mainhandIs("*" + HAS) && !en.toString().contains("Applied enchantment"), en.toString());
        }

        // 3. enchanted book + anvil
        arena();
        tool("minecraft:enchanted_book[minecraft:stored_enchantments={\"" + ENCH + "\":1}]");
        check("enchanted book with stored_enchantments", mainhandIs("minecraft:enchanted_book[minecraft:stored_enchantments~[{enchantments:\"" + ENCH + "\"}]]"), held());
        ItemStack book = EnchantedVeinminerTest.mainhand();
        tool("minecraft:diamond_pickaxe[minecraft:enchantments={\"minecraft:efficiency\":5}]");
        ItemStack out = EnchantedVeinminerTest.anvil(EnchantedVeinminerTest.mainhand(), book);
        EnchantedVeinminerTest.setMainhand(out);
        check("anvil: book onto pickaxe", mainhandIs("minecraft:diamond_pickaxe" + HAS) && mainhandIs("*[minecraft:enchantments~[{enchantments:\"minecraft:efficiency\",levels:5}]]"), held());
        place("minecraft:iron_ore", IRON);
        mine();
        check("anvil-enchanted pickaxe veinmines", remaining("minecraft:iron_ore", IRON) == 0, "left=" + remaining("minecraft:iron_ore", IRON));
        for (String t : new String[] {"minecraft:diamond_shovel", "minecraft:diamond_axe", "minecraft:diamond_sword"}) {
            tool(t);
            out = EnchantedVeinminerTest.anvil(EnchantedVeinminerTest.mainhand(), book);
            EnchantedVeinminerTest.setMainhand(out);
            check("anvil: book refused on " + t, out.isEmpty() || !mainhandIs("*" + HAS), held());
        }
        out = EnchantedVeinminerTest.anvil(book, book);
        EnchantedVeinminerTest.setMainhand(out);
        check("anvil: two books don't make level II", !mainhandIs("*[minecraft:stored_enchantments~[{enchantments:\"" + ENCH + "\",levels:{min:2}}]]"), held());

        // 4. where it comes from
        int pickHits = EnchantedVeinminerTest.tableRolls("minecraft:iron_pickaxe", ENCH, 30, 3000);
        int bookHits = EnchantedVeinminerTest.tableRolls("minecraft:book", ENCH, 30, 3000);
        int shovelHits = EnchantedVeinminerTest.tableRolls("minecraft:diamond_shovel", ENCH, 30, 3000);
        int lowHits = EnchantedVeinminerTest.tableRolls("minecraft:iron_pickaxe", ENCH, 5, 3000);
        int effHits = EnchantedVeinminerTest.tableRolls("minecraft:iron_pickaxe", "minecraft:efficiency", 30, 3000);
        int fortHits = EnchantedVeinminerTest.tableRolls("minecraft:iron_pickaxe", "minecraft:fortune", 30, 3000);
        info("table rolls /3000 at level 30: veinminer pickaxe=" + pickHits + " book=" + bookHits + " shovel=" + shovelHits
            + ", level 5 pickaxe=" + lowHits + "; efficiency=" + effHits + " fortune=" + fortHits);
        check("enchanting table offers it for pickaxes and books", pickHits > 0 && bookHits > 0, "pickaxe=" + pickHits + " book=" + bookHits);
        check("enchanting table never offers it for shovels", shovelHits == 0, "shovel=" + shovelHits);
        tool("minecraft:iron_pickaxe[minecraft:enchantments={\"" + ENCH + "\":1}]");
        for (String tag : new String[] {"in_enchanting_table", "non_treasure", "tradeable", "on_random_loot", "on_traded_equipment", "tooltip_order"})
            check("in #minecraft:" + tag, mainhandIs("*[minecraft:enchantments~[{enchantments:\"#minecraft:" + tag + "\"}]]"), "");
        for (String tag : new String[] {"treasure", "curse", "double_trade_price", "exclusive_set/mining"})
            check("not in #minecraft:" + tag, !mainhandIs("*[minecraft:enchantments~[{enchantments:\"#minecraft:" + tag + "\"}]]"), "");

        // 5. Veinminer's join hint and menu
        chat();
        cmd("execute as VeinTester run function veinminer:player/welcome");
        List<String> hello = chat();
        info("welcome: " + hello);
        check("join hint says the enchantment is needed",
            hello.contains("⛏ Veinminer: sneak while mining an ore with a pickaxe to break the whole vein. Your pickaxe needs the Veinminer enchantment. [Toggle] {clicks=1}"),
            hello.toString());
        cmd("execute as VeinTester run function veinminer:settings");
        List<String> menu = chat();
        for (String m : menu) info("menu| " + m);
        check("settings menu says the enchantment is needed", menu.size() == 12 && menu.get(1).contains("Your pickaxe needs the Veinminer enchantment."),
            menu.size() + " lines");

        // 6. /reload keeps everything
        cmd("reload");
        ticks(5);
        check("after /reload: one requirement line, still gated", requiresCount() == 1 && ironVein("minecraft:iron_pickaxe") == 5,
            "requires=" + requiresCount());
        chat();
        ticks(45);
        check("after /reload: no warning", chat().stream().noneMatch(Scenarios::isWarning) && score("#problem") == 0, "problem=" + score("#problem"));

        // 7. Veinminer missing
        pack("disable", BASE);
        c = chat();
        info("chat without Veinminer: " + c);
        check("warns when Veinminer is missing", score("#problem") == 1 && c.stream().anyMatch(m -> isWarning(m) && m.contains("version 1.1.0 or newer") && m.contains("{clicks=1}")),
            c.toString());
        ticks(50);
        c = chat();
        check("warning isn't repeated to the same player", c.stream().noneMatch(Scenarios::isWarning), c.toString());
        cmd("scoreboard players set VeinTester enchanted_veinminer.left 1");
        ticks(45);
        c = chat();
        check("warning repeats after a rejoin", c.stream().anyMatch(Scenarios::isWarning), c.toString());
        pack("enable", BASE);
        chat();
        ticks(45);
        c = chat();
        check("warning stops once Veinminer is back", score("#problem") == 0 && c.stream().noneMatch(Scenarios::isWarning), c.toString());
        check("gate works again", ironVein("minecraft:iron_pickaxe") == 5 && ironVein("minecraft:iron_pickaxe" + "[minecraft:enchantments={\"" + ENCH + "\":1}]") == 0, "");

        // 8. Veinminer 1.0.0 (no add-on support)
        pack("disable", BASE);
        pack("enable", OLD);
        List<String> ver = cmd("data get storage veinminer:meta version");
        c = chat();
        check("warns with Veinminer 1.0.0", ver.toString().contains("1.0.0") && score("#problem") == 1 && c.stream().anyMatch(Scenarios::isWarning), ver + " " + c);
        left = ironVein("minecraft:iron_pickaxe");
        info("Veinminer 1.0.0 + add-on, unenchanted pickaxe: " + (6 - left) + " of 6 mined");
        pack("disable", OLD);
        pack("enable", BASE);
        chat();
        ticks(45);
        check("back on 1.1.0: no warning, gated", score("#problem") == 0 && chat().stream().noneMatch(Scenarios::isWarning)
            && ironVein("minecraft:iron_pickaxe") == 5, "problem=" + score("#problem"));

        // 9. add-on switched off with /datapack disable (no restart)
        pack("disable", ADDON);
        left = ironVein("minecraft:iron_pickaxe");
        tool("minecraft:iron_pickaxe");
        en = cmd("enchant VeinTester " + ENCH);
        check("add-on disabled: Veinminer works without the enchantment again", left == 0 && requiresCount() == -1, "left=" + left + " requires=" + requiresCount());
        info("add-on disabled, before restart: /enchant -> " + en);
        pack("enable", ADDON);
        check("add-on re-enabled: gated again", ironVein("minecraft:iron_pickaxe") == 5 && requiresCount() == 1, "requires=" + requiresCount());

        // 10. uninstall
        List<String> un = cmd("execute as VeinTester run function enchanted_veinminer:uninstall");
        List<String> objs = cmd("scoreboard objectives list");
        check("uninstall removes the add-on's objectives", objs.stream().noneMatch(o -> o.contains("enchanted_veinminer")), objs + " " + un);
    }
}
