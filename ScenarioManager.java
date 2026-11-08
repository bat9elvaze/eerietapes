package com.eerietapes;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Вся логика «просмотра кассеты»: телепорт в мир кассеты, таймер, правила, события,
 * награда за выживание, возврат домой. Плюс случайное событие «светящаяся кассета» в обычном мире.
 */
@Mod.EventBusSubscriber(modid = EerieTapes.MODID)
public class ScenarioManager {

    public static final ResourceKey<Level> TAPE_DIM =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(EerieTapes.MODID, "tape_world"));

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record Line(int tick, String text) {}

    // ---- Голос на плёнке -------------------------------------------------------------------
    private static final List<Line> HOUSE_LINES = List.of(
            new Line(40, "Привет, малыш. Ты ведь остался один дома, да? Не бойся. Я научу тебя, как дожить до утра."),
            new Line(260, "Правило первое: не выходи из дома. Что бы ты ни услышал за дверью."),
            new Line(600, "Правило второе: если я скажу «тихо» — садись и не двигайся. Совсем."),
            new Line(1000, "Правило третье: если кто-то в доме похож на твою маму... это не твоя мама."),
            new Line(2600, "Ты хорошо держишься. Но ты ведь слышишь, как кто-то стучит?"),
            new Line(4200, "Осталось немного. Только не открывай дверь. Даже если позовут по имени.")
    );
    private static final List<Line> FOREST_LINES = List.of(
            new Line(40, "Ты в лесу. Не спрашивай, как ты здесь оказался. Просто дождись рассвета."),
            new Line(260, "Они двигаются, только когда ты на них не смотришь."),
            new Line(600, "Не своди с них глаз. Не моргай. Никогда."),
            new Line(1400, "Слышишь шаги? Это не эхо."),
            new Line(3000, "Если не видишь их — значит, они уже ближе."),
            new Line(5200, "Почти утро. Продержись.")
    );

    // ---- Состояние сессии ------------------------------------------------------------------
    private static class Session {
        final TapeType type;
        final ResourceKey<Level> originDim;
        final Vec3 originPos;
        final float yRot, xRot;
        final BlockPos center;
        final int total;
        final ServerBossEvent bar;
        int ticksLeft, tick;

        // дом
        int nextHideAt = 1100;
        boolean hideActive;
        int hideTicksLeft;
        Vec3 hidePos;
        MimicEntity hideMimic;
        int punishMimics;
        int lastWarnTick = -1000;

        Session(TapeType type, ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot, BlockPos center) {
            this.type = type;
            this.originDim = dim;
            this.originPos = pos;
            this.yRot = yRot;
            this.xRot = xRot;
            this.center = center;
            this.total = type.durationTicks;
            this.ticksLeft = total;
            this.bar = new ServerBossEvent(Component.literal("Дождись конца записи"),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            this.bar.setDarkenScreen(true);
            this.bar.setCreateWorldFog(true);
        }
    }

    public static boolean isInSession(net.minecraft.world.entity.player.Player p) {
        return SESSIONS.containsKey(p.getUUID());
    }

    // ---- Запуск ----------------------------------------------------------------------------
    public static boolean start(ServerPlayer player, TapeType type) {
        if (SESSIONS.containsKey(player.getUUID())) return false;

        ServerLevel tape = player.server.getLevel(TAPE_DIM);
        if (tape == null) {
            player.sendSystemMessage(Component.literal("Измерение кассеты не загрузилось (проверь data/eerietapes/dimension).")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        BlockPos center = findLand(tape, player.getRandom());
        if (center == null) {
            player.sendSystemMessage(Component.literal("Не нашлось места для записи, попробуй ещё раз.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (type == TapeType.HOUSE) buildHouse(tape, center);

        Session s = new Session(type, player.level().dimension(), player.position(),
                player.getYRot(), player.getXRot(), center);
        SESSIONS.put(player.getUUID(), s);
        s.bar.addPlayer(player);

        player.teleportTo(tape, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0F, 0.0F);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
        title(player, type == TapeType.HOUSE ? "ЗАПИСЬ №1: ДОМ" : "ЗАПИСЬ №2: ЛЕС");
        return true;
    }

    private static BlockPos findLand(ServerLevel level, RandomSource r) {
        for (int i = 0; i < 30; i++) {
            int x = (r.nextBoolean() ? 1 : -1) * (3000 + r.nextInt(20000));
            int z = (r.nextBoolean() ? 1 : -1) * (3000 + r.nextInt(20000));
            level.getChunk(x >> 4, z >> 4); // сгенерировать чанк
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > level.getSeaLevel() && level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    // ---- Дом -------------------------------------------------------------------------------
    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private static void buildHouse(ServerLevel l, BlockPos c) {
        int by = c.getY() - 1; // уровень пола
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int x = c.getX() + dx, z = c.getZ() + dz;
                for (int y = by - 4; y <= by; y++) {
                    set(l, x, y, z, (y == by ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState());
                }
                for (int y = by + 1; y <= by + 10; y++) set(l, x, y, z, air);

                if (Math.abs(dx) > 5 || Math.abs(dz) > 5) continue;
                set(l, x, by, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                set(l, x, by + 5, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                boolean wall = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                if (!wall) continue;
                boolean window = (Math.abs(dx) == 5 && Math.abs(dz) == 2) || (Math.abs(dz) == 5 && Math.abs(dx) == 2);
                for (int y = 1; y <= 4; y++) {
                    BlockState st = (window && (y == 2 || y == 3)) ? Blocks.GLASS.defaultBlockState()
                            : Blocks.BIRCH_PLANKS.defaultBlockState();
                    set(l, x, by + y, z, st);
                }
            }
        }
        // дверь в южной стене
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        set(l, c.getX(), by + 1, c.getZ() + 5, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(l, c.getX(), by + 2, c.getZ() + 5, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // немного обстановки
        int y0 = by + 1;
        set(l, c.getX() - 4, y0, c.getZ() - 4, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(l, c.getX() - 4, y0, c.getZ() + 4, Blocks.TORCH.defaultBlockState());
        set(l, c.getX() + 4, y0, c.getZ() + 4, Blocks.TORCH.defaultBlockState());
        BlockPos chestPos = new BlockPos(c.getX() + 4, y0, c.getZ() - 4);
        l.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        if (l.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.BREAD, 6));
            chest.setItem(1, new ItemStack(Items.COOKED_PORKCHOP, 4));
            chest.setItem(2, new ItemStack(Items.TORCH, 8));
            chest.setItem(3, new ItemStack(Items.STONE_SWORD));
        }
    }

    // ---- Тик -------------------------------------------------------------------------------
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        Session s = SESSIONS.get(sp.getUUID());
        if (s == null) {
            overworldEvent(sp);
            return;
        }
        ServerLevel tape = sp.serverLevel();
        if (s.tick > 10 && tape.dimension() != TAPE_DIM) { // игрок вышел из мира кассеты другим способом
            cleanup(sp.server, sp.getUUID(), s);
            return;
        }
        s.tick++;
        s.ticksLeft--;
        s.bar.setProgress(Mth.clamp(s.ticksLeft / (float) s.total, 0.0F, 1.0F));

        if (s.ticksLeft <= 0) {
            finish(sp, s, true);
            return;
        }
        for (Line line : (s.type == TapeType.HOUSE ? HOUSE_LINES : FOREST_LINES)) {
            if (line.tick() == s.tick) say(sp, line.text());
        }
        if (s.type == TapeType.HOUSE) tickHouse(sp, s, tape);
        else tickForest(sp, s, tape);
    }

    private static void tickHouse(ServerPlayer sp, Session s, ServerLevel tape) {
        RandomSource r = sp.getRandom();
        BlockPos door = s.center.offset(0, 1, 6);

        // стук и голос за дверью
        if (!s.hideActive && s.tick % 400 == 200) {
            tape.playSound(null, door, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 0.7F);
            if ((s.tick / 400) % 2 == 1) {
                tape.playSound(null, door, SoundEvents.VILLAGER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
        }

        // правило 1: не выходить из дома
        if (s.tick % 20 == 0) {
            double dx = sp.getX() - (s.center.getX() + 0.5), dz = sp.getZ() - (s.center.getZ() + 0.5);
            if (dx * dx + dz * dz > 8 * 8 && s.tick - s.lastWarnTick > 100) {
                s.lastWarnTick = s.tick;
                say(sp, "Я же просил — не выходи из дома.");
                sp.hurt(sp.damageSources().magic(), 4.0F);
                sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
                if (s.punishMimics < 3) {
                    s.punishMimics++;
                    BlockPos p = ringPos(tape, sp.blockPosition(), r, 10, 14);
                    if (p != null) spawnMob(tape, ModEntities.MIMIC.get(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                }
            }
        }

        // правило 2: «тихо» — присесть и не двигаться
        if (!s.hideActive && s.tick >= s.nextHideAt && s.ticksLeft > 500) {
            s.hideActive = true;
            s.hideTicksLeft = 200;
            s.hidePos = null;
            title(sp, "ТИХО.");
            say(sp, "Тихо. Сядь. Не двигайся.");
            sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 220, 0, false, false));
            s.hideMimic = spawnMob(tape, ModEntities.MIMIC.get(),
                    s.center.getX() + 0.5, s.center.getY(), s.center.getZ() - 2.5);
            if (s.hideMimic != null) s.hideMimic.setNoAi(true);
        }
        if (s.hideActive) {
            s.hideTicksLeft--;
            if (s.hideTicksLeft == 170) s.hidePos = sp.position(); // 1.5 сек, чтобы успеть присесть
            if (s.hidePos != null) {
                boolean still = sp.isShiftKeyDown() && sp.distanceToSqr(s.hidePos) < 2.0;
                if (!still) {
                    title(sp, "ОНО ТЕБЯ ВИДИТ");
                    say(sp, "Я сказал — не двигайся.");
                    if (s.hideMimic != null && s.hideMimic.isAlive()) {
                        s.hideMimic.setNoAi(false);
                        s.hideMimic.setTarget(sp);
                    }
                    s.hideActive = false;
                    s.nextHideAt = s.tick + 1200;
                    return;
                }
            }
            if (s.hideTicksLeft <= 0) {
                if (s.hideMimic != null && s.hideMimic.isAlive()) {
                    tape.sendParticles(ParticleTypes.LARGE_SMOKE, s.hideMimic.getX(), s.hideMimic.getY() + 1,
                            s.hideMimic.getZ(), 20, 0.3, 0.8, 0.3, 0.02);
                    s.hideMimic.discard();
                }
                say(sp, "...Ушло. Продолжай ждать.");
                s.hideActive = false;
                s.nextHideAt = s.tick + 900 + r.nextInt(600);
            }
        }
    }

    private static void tickForest(ServerPlayer sp, Session s, ServerLevel tape) {
        if (s.tick >= 300 && s.tick % 400 == 300) {
            int cap = 2 + s.tick / 2400;
            int cur = tape.getEntitiesOfClass(WatcherEntity.class, new AABB(sp.blockPosition()).inflate(100)).size();
            if (cur < cap) {
                BlockPos p = ringPos(tape, sp.blockPosition(), sp.getRandom(), 24, 32);
                if (p != null) spawnMob(tape, ModEntities.WATCHER.get(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            }
        }
        if (s.tick % 300 == 150) {
            sp.playNotifySound(SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.5F, 0.5F);
        }
    }

    // ---- Завершение ------------------------------------------------------------------------
    private static void finish(ServerPlayer sp, Session s, boolean success) {
        cleanup(sp.server, sp.getUUID(), s);
        sp.removeEffect(MobEffects.DARKNESS);
        sp.removeEffect(MobEffects.CONFUSION);

        ServerLevel dest = sp.server.getLevel(s.originDim);
        if (dest == null) dest = sp.server.overworld();
        sp.teleportTo(dest, s.originPos.x, s.originPos.y, s.originPos.z, s.yRot, s.xRot);

        if (success) {
            title(sp, "ЗАПИСЬ ОКОНЧЕНА");
            say(sp, "Ты досмотрел до конца. Это редко кому удаётся.");
            RandomSource r = sp.getRandom();
            ItemHandlerHelper.giveItemToPlayer(sp, new ItemStack(Items.GOLDEN_APPLE, 2));
            ItemHandlerHelper.giveItemToPlayer(sp, new ItemStack(Items.DIAMOND, 1 + r.nextInt(3)));
            if (r.nextInt(4) == 0) {
                ItemHandlerHelper.giveItemToPlayer(sp, new ItemStack(r.nextBoolean() ? ModItems.TAPE_HOUSE.get() : ModItems.TAPE_FOREST.get()));
            }
        }
    }

    private static void cleanup(MinecraftServer server, UUID id, Session s) {
        s.bar.removeAllPlayers();
        ServerLevel tape = server.getLevel(TAPE_DIM);
        if (tape != null) {
            AABB box = new AABB(s.center).inflate(150);
            for (Mob m : tape.getEntitiesOfClass(Mob.class, box, e -> e instanceof WatcherEntity || e instanceof MimicEntity)) {
                m.discard();
            }
        }
        SESSIONS.remove(id);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            Session s = SESSIONS.get(sp.getUUID());
            if (s != null) cleanup(sp.server, sp.getUUID(), s); // проиграл: вещи остаются в мире кассеты
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            Session s = SESSIONS.get(sp.getUUID());
            if (s != null) cleanup(sp.server, sp.getUUID(), s);
        }
    }

    /** Если игрок заходит в мир, а стоит в мире кассеты без сессии — отправляем домой. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp
                && sp.level().dimension() == TAPE_DIM && !SESSIONS.containsKey(sp.getUUID())) {
            ServerLevel ow = sp.server.overworld();
            BlockPos p = ow.getSharedSpawnPos();
            int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
            sp.teleportTo(ow, p.getX() + 0.5, y, p.getZ() + 0.5, 0.0F, 0.0F);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        SESSIONS.clear();
    }

    /** В мире кассеты не спавнятся обычные мобы — только наши. */
    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck e) {
        if (e.getLevel().getLevel().dimension() == TAPE_DIM && e.getSpawnType() == MobSpawnType.NATURAL) {
            e.setResult(Event.Result.DENY);
        }
    }

    // ---- Случайное событие в обычном мире --------------------------------------------------
    private static void overworldEvent(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        if (level.dimension() != Level.OVERWORLD || sp.tickCount % 1200 != 0) return;
        long t = level.getDayTime() % 24000L;
        if (t < 13000 || t > 23000) return; // только ночью

        CompoundTag data = sp.getPersistentData();
        long now = level.getGameTime();
        long last = data.getLong("eerietapes_last_event");
        if (last != 0 && now - last < 24000) return; // не чаще раза в игровые сутки
        RandomSource r = sp.getRandom();
        if (r.nextInt(6) != 0) return;

        BlockPos p = ringPos(level, sp.blockPosition(), r, 10, 18);
        if (p == null) return;
        data.putLong("eerietapes_last_event", now);

        ItemStack stack = new ItemStack(r.nextBoolean() ? ModItems.TAPE_HOUSE.get() : ModItems.TAPE_FOREST.get());
        ItemEntity item = new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, stack);
        item.setGlowingTag(true);
        item.setUnlimitedLifetime();
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);

        sp.displayClientMessage(Component.literal("Где-то рядом щёлкнул магнитофон...")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
        sp.playNotifySound(SoundEvents.LEVER_CLICK, SoundSource.AMBIENT, 1.0F, 0.5F);
    }

    // ---- Команды для теста: /tape start house|forest, /tape stop ---------------------------
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("tape").requires(src -> src.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.literal("house").executes(c -> run(c.getSource(), TapeType.HOUSE)))
                        .then(Commands.literal("forest").executes(c -> run(c.getSource(), TapeType.FOREST))))
                .then(Commands.literal("stop").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Session s = SESSIONS.get(p.getUUID());
                    if (s != null) finish(p, s, false);
                    return 1;
                })));
    }

    private static int run(CommandSourceStack src, TapeType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return start(src.getPlayerOrException(), type) ? 1 : 0;
    }

    // ---- Утилиты ---------------------------------------------------------------------------
    private static BlockPos ringPos(ServerLevel level, BlockPos origin, RandomSource r, int minD, int maxD) {
        double a = r.nextDouble() * Math.PI * 2;
        double d = minD + r.nextInt(maxD - minD + 1);
        int x = origin.getX() + (int) (Math.cos(a) * d);
        int z = origin.getZ() + (int) (Math.sin(a) * d);
        if (!level.hasChunkAt(new BlockPos(x, 64, z))) return null;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static <T extends Mob> T spawnMob(ServerLevel level, EntityType<T> type, double x, double y, double z) {
        T mob = type.create(level);
        if (mob == null) return null;
        mob.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        mob.setPersistenceRequired();
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal("▶ " + text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.playNotifySound(SoundEvents.LEVER_CLICK, SoundSource.AMBIENT, 0.6F, 0.6F);
    }

    private static void title(ServerPlayer p, String text) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text).withStyle(ChatFormatting.DARK_RED)));
    }
}
