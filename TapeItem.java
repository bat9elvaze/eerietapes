package com.eerietapes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Кассета. ПКМ (удерживать ~4 секунды) — «смотришь запись», потом тебя утаскивает внутрь. */
public class TapeItem extends Item {
    private final TapeType type;

    public TapeItem(TapeType type, Properties props) {
        super(props);
        this.type = type;
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack) { return 80; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ScenarioManager.isInSession(player)) return InteractionResultHolder.fail(stack);

        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 0.6F);
            player.sendSystemMessage(Component.literal("Ты вставляешь кассету в старый магнитофон. Экран мигает...")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            if (ScenarioManager.start(sp, type) && !sp.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide) entity.removeEffect(MobEffects.CONFUSION);
    }
}
