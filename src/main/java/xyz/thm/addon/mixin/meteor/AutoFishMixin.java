/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.FishingHookAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.player.AutoFish;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.utils.RotationUtils;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = AutoFish.class, remap = false)
public class AutoFishMixin {
    @Shadow @Final private SettingGroup sgGeneral;
    @Unique private Setting<Boolean> thm$randomRotate;
    @Unique private Setting<Boolean> thm$randomMove;
    @Unique private LocalPlayer thm$player;
    @Unique private ClientLevel thm$level;
    @Unique private Vec3 thm$origin, thm$back;
    @Unique private Vec3 thm$step = Vec3.ZERO;
    @Unique private int thm$waiting, thm$moveTicks;
    @Unique private boolean thm$returning;
    @Unique private int thm$lookTick = -1;
    @Unique private float thm$yaw, thm$pitch, thm$awayYaw, thm$awayPitch;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$settings(CallbackInfo ci) {
        thm$randomRotate = sgGeneral.add(new BoolSetting.Builder()
            .name("random-rotate").description("Looks around after a catch, then returns before recasting.")
            .defaultValue(false).onChanged(enabled -> { if (!enabled) thm$reset(true); }).build());
        thm$randomMove = sgGeneral.add(new BoolSetting.Builder()
            .name("random-move").description("Takes a short backward step and returns after a catch.")
            .defaultValue(false).onChanged(enabled -> { if (!enabled) thm$reset(true); }).build());
    }

    @WrapOperation(method = "reel", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/systems/modules/player/AutoFish;useRod()Z"))
    private boolean thm$afterCatch(AutoFish fish, Operation<Boolean> original) {
        boolean caught = mc.player != null && mc.player.fishing != null
            && mc.player.fishing.getHookedIn() == null
            && ((FishingHookAccessor) mc.player.fishing).meteor$hasCaughtFish();
        boolean used = original.call(fish);
        if (!used || !caught || (!thm$randomRotate.get() && !thm$randomMove.get())) return used;

        thm$reset(true);
        thm$player = mc.player;
        thm$level = mc.level;
        thm$waiting = 40;
        if (thm$randomMove.get()) thm$startMovement(mc.player.position(), mc.player.getYRot(), Utils.random(0.2, 0.6));
        if (thm$randomRotate.get()) {
            float yaw = mc.player.getYRot();
            float offset = (float) Utils.random(30, 150) * (Utils.random(0, 2) == 0 ? -1 : 1);
            thm$startLook(yaw, mc.player.getXRot(), yaw + offset, (float) Utils.random(-60, 60));
        }
        return true;
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void thm$betweenCatches(TickEvent.Pre event, CallbackInfo ci) {
        if (mc.player == null || mc.level == null) {
            thm$reset(false);
            ci.cancel();
            return;
        }
        if (thm$player == null) return;
        if (mc.player != thm$player || mc.level != thm$level) {
            thm$reset(false);
            return;
        }
        thm$step = Vec3.ZERO;
        if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
            || mc.options.keyRight.isDown() || mc.options.keyJump.isDown() || mc.gui.screen() != null
            || mc.player.input.getMoveVector().x != 0 || mc.player.input.getMoveVector().y != 0) {
            thm$reset(true);
            return;
        }
        if (mc.player.fishing != null) {
            // Wait for the server to remove the reeled bobber before moving or looking away.
            if (--thm$waiting <= 0) thm$reset(true);
            else ci.cancel();
            return;
        }
        thm$waiting = 0;

        if (thm$moveTicks > 0) {
            if (!mc.player.onGround() || mc.player.isInWater() || mc.player.isPassenger()
                || mc.player.isFallFlying() || mc.player.position().distanceToSqr(thm$origin) > 4) thm$moveTicks = 0;
            else thm$step = thm$movementStep(mc.player.position());
        }
        if (thm$moveTicks > 0) return;
        Vec2 look = thm$nextLook();
        if (look != null) RotationUtils.getInstance().setRotationClient(look.x, look.y);
        if (thm$lookTick < 0) thm$reset(false);
    }

    @Inject(method = "cast", at = @At("HEAD"), cancellable = true)
    private void thm$waitBeforeCast(CallbackInfo ci) {
        if (thm$player != null) ci.cancel();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void thm$walk(PlayerMoveEvent event) {
        if (thm$player == null || mc.player != thm$player || mc.level != thm$level
            || thm$step == Vec3.ZERO || event.type != MoverType.SELF) return;
        Vec3 step = thm$step;
        thm$step = Vec3.ZERO;
        AABB box = mc.player.getBoundingBox().move(step);
        AABB floor = new AABB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY, box.maxZ);
        if (!mc.level.noCollision(mc.player, box) || !mc.level.getBlockCollisions(mc.player, floor).iterator().hasNext()) {
            if (thm$returning) thm$moveTicks = 0;
            else thm$returning = true;
            return;
        }
        event.movement = new Vec3(step.x, event.movement.y, step.z);
    }

    @Inject(method = {"onActivate", "onDeactivate"}, at = @At("HEAD"))
    private void thm$clearCycle(CallbackInfo ci) {
        thm$reset(true);
    }

    @EventHandler
    private void thm$onLeave(GameLeftEvent event) {
        thm$reset(false);
    }

    @Unique
    private void thm$reset(boolean restore) {
        if (restore && thm$lookTick >= 0 && mc.player == thm$player && mc.level == thm$level && thm$player != null) {
            RotationUtils.getInstance().setRotationClient(thm$yaw, thm$pitch);
        }
        thm$player = null;
        thm$level = null;
        thm$origin = thm$back = null;
        thm$step = Vec3.ZERO;
        thm$waiting = thm$moveTicks = 0;
        thm$lookTick = -1;
    }

    @Unique
    void thm$startMovement(Vec3 origin, float yaw, double distance) {
        thm$origin = origin;
        double radians = Math.toRadians(yaw);
        thm$back = origin.add(Math.sin(radians) * distance, 0, -Math.cos(radians) * distance);
        thm$returning = false;
        thm$moveTicks = 40;
    }

    @Unique
    Vec3 thm$movementStep(Vec3 position) {
        if (thm$moveTicks <= 0) return Vec3.ZERO;
        Vec3 target = thm$returning ? thm$origin : thm$back;
        Vec3 delta = new Vec3(target.x - position.x, 0, target.z - position.z);
        if (delta.horizontalDistanceSqr() < 0.000625) {
            if (thm$returning) { thm$moveTicks = 0; return Vec3.ZERO; }
            thm$returning = true;
            delta = new Vec3(thm$origin.x - position.x, 0, thm$origin.z - position.z);
        }
        if (--thm$moveTicks <= 0) return Vec3.ZERO;
        return delta.normalize().scale(Math.min(0.06, delta.horizontalDistance()));
    }

    @Unique
    void thm$startLook(float yaw, float pitch, float awayYaw, float awayPitch) {
        thm$yaw = yaw;
        thm$pitch = pitch;
        thm$awayYaw = yaw + Mth.wrapDegrees(awayYaw - yaw);
        thm$awayPitch = Math.clamp(awayPitch, -90, 90);
        thm$lookTick = 0;
    }

    @Unique
    Vec2 thm$nextLook() {
        if (thm$lookTick < 0) return null;
        int tick = ++thm$lookTick;
        float fraction = tick <= 8 ? tick / 8f : tick <= 14 ? 1 : (22 - tick) / 8f;
        float eased = fraction * fraction * (3 - 2 * fraction);
        Vec2 rotation = new Vec2(thm$yaw + (thm$awayYaw - thm$yaw) * eased,
            thm$pitch + (thm$awayPitch - thm$pitch) * eased);
        if (tick == 22) thm$lookTick = -1;
        return rotation;
    }
}
