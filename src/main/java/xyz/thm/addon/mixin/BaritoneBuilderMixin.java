/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import baritone.api.process.IBuilderProcess;
import baritone.api.BaritoneAPI;
import baritone.api.process.PathingCommand;
import baritone.api.schematic.ISchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.modules.BetterBaritoneBuild;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Pseudo
@Mixin(targets = "baritone.process.BuilderProcess", remap = false)
public abstract class BaritoneBuilderMixin {
    @Inject(method = "build(Ljava/lang/String;Lbaritone/api/schematic/ISchematic;Lnet/minecraft/core/Vec3i;)V", at = @At("RETURN"))
    private void thm$buildStarted(CallbackInfo ci) {
        BetterBaritoneBuild module = BetterBaritoneBuild.get();
        if (module != null && module.isActive())
            thm$setSchematic(module.prepareBuild((IBuilderProcess) this, thm$getSchematic(), thm$getOrigin()));
    }

    // Meteor's release JAR shortens private method names; upstream development JARs do not.
    @Redirect(method = {
        "assemble(Lbaritone/process/BuilderProcess$BuilderCalculationContext;Ljava/util/List;Z)Lbaritone/api/pathing/goals/Goal;",
        "a(Lbaritone/process/BuilderProcess$BuilderCalculationContext;Ljava/util/List;Z)Lbaritone/api/pathing/goals/Goal;"
    }, at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"), require = 1)
    private Set<Map.Entry<BlockState, Integer>> thm$missingMaterials(Map<BlockState, Integer> missing) {
        BetterBaritoneBuild module = BetterBaritoneBuild.get();
        if (module != null && module.isActive()) module.missingMaterials((IBuilderProcess) this, missing);
        return missing.entrySet();
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void thm$stripLogs(CallbackInfoReturnable<PathingCommand> cir) {
        BetterBaritoneBuild module = BetterBaritoneBuild.get();
        if (module != null && module.isActive() && module.stripNextLog((IBuilderProcess) this, ((IBuilderProcess) this).getApproxPlaceable())) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
            cir.setReturnValue(new PathingCommand(null, baritone.api.process.PathingCommandType.REQUEST_PAUSE));
        }
    }

    @Inject(method = "onLostControl", at = @At("HEAD"))
    private void thm$buildStopped(CallbackInfo ci) {
        BetterBaritoneBuild module = BetterBaritoneBuild.get();
        if (module != null) module.buildStopped((IBuilderProcess) this);
    }

    private ISchematic thm$getSchematic() {
        return (ISchematic) thm$field(ISchematic.class, "schematic");
    }

    private void thm$setSchematic(ISchematic schematic) {
        thm$field(ISchematic.class, "schematic", schematic);
    }

    private Vec3i thm$getOrigin() {
        return (Vec3i) thm$field(Vec3i.class, "origin");
    }

    private Object thm$field(Class<?> fieldType, String name) {
        Field field = thm$findField(fieldType, name);
        try {
            field.setAccessible(true);
            return field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read Baritone " + name + " field", e);
        }
    }

    private void thm$field(Class<?> fieldType, String name, Object value) {
        Field field = thm$findField(fieldType, name);
        try {
            field.setAccessible(true);
            field.set(this, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not write Baritone " + name + " field", e);
        }
    }

    // Baritone's release JAR renames these fields to public `a` fields, which Mixin aliases cannot shadow.
    private Field thm$findField(Class<?> fieldType, String name) {
        for (Class<?> type = getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == fieldType && (field.getName().equals(name) || field.getName().equals("a"))) return field;
            }
        }
        throw new IllegalStateException("Baritone field not found: " + name);
    }
}
