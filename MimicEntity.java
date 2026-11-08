package com.eerietapes;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * «Подражатель»: высокий, слишком улыбчивый, копирует чужие звуки
 * (жители, коровы, дыхание). С отключённым ИИ просто стоит и смотрит на игрока.
 */
public class MimicEntity extends Monster {
    private static final SoundEvent[] VOICES = {
            SoundEvents.VILLAGER_AMBIENT, SoundEvents.VILLAGER_NO, SoundEvents.COW_AMBIENT,
            SoundEvents.CHICKEN_AMBIENT, SoundEvents.PLAYER_BREATH, SoundEvents.ZOMBIE_VILLAGER_AMBIENT,
            SoundEvents.WOLF_WHINE
    };

    public MimicEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;

        if (random.nextInt(200) == 0) {
            playSound(VOICES[random.nextInt(VOICES.length)], 1.2F, 0.5F + random.nextFloat() * 0.3F);
        }
        // Замороженный (NoAI) подражатель всё равно пялится на ближайшего игрока.
        if (isNoAi()) {
            Player p = level().getNearestPlayer(this, 24.0);
            if (p != null) {
                float yaw = (float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * (180.0 / Math.PI)) - 90.0F;
                setYRot(yaw);
                setYHeadRot(yaw);
                setYBodyRot(yaw);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
