package com.eerietapes;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * «Наблюдатель»: бледная тварь, которая замирает, пока ты на неё смотришь,
 * и несётся на тебя, как только отведёшь взгляд. Пока на него смотрят — неуязвим.
 */
public class WatcherEntity extends Monster {

    public WatcherEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FreezeWhenWatchedGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, false));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Смотрит ли на меня хоть один игрок (в конусе ~60° и с прямой видимостью). */
    public boolean isWatched() {
        for (Player p : level().players()) {
            if (p.isSpectator() || p.distanceToSqr(this) > 64.0 * 64.0) continue;
            Vec3 look = p.getViewVector(1.0F).normalize();
            Vec3 toMe = new Vec3(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ()).normalize();
            if (look.dot(toMe) > 0.5 && p.hasLineOfSight(this)) return true;
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isWatched() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    static class FreezeWhenWatchedGoal extends Goal {
        private final WatcherEntity mob;

        FreezeWhenWatchedGoal(WatcherEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override public boolean canUse() { return mob.isWatched(); }
        @Override public boolean canContinueToUse() { return mob.isWatched(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            mob.getNavigation().stop();
            Vec3 m = mob.getDeltaMovement();
            mob.setDeltaMovement(0, m.y, 0);
        }
    }
}
