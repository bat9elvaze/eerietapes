package com.eerietapes;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Гуманоидная модель (как у зомби) с нашей текстурой. Вытянутый по высоте — выглядит неправильно. */
public class CreepyRenderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    private final ResourceLocation texture;
    private final float yScale;

    public CreepyRenderer(EntityRendererProvider.Context ctx, ResourceLocation texture, float yScale) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        this.texture = texture;
        this.yScale = yScale;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        pose.scale(0.92F, yScale, 0.92F);
    }
}
