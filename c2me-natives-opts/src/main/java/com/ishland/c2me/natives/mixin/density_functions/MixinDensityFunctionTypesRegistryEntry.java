package com.ishland.c2me.natives.mixin.density_functions;

import com.google.common.collect.ImmutableMap;
import com.ishland.c2me.natives.common.CompiledDensityFunctionImpl;
import com.ishland.c2me.natives.common.DensityFunctionUtils;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DensityFunctionTypes.class_7051.class)
public abstract class MixinDensityFunctionTypesRegistryEntry implements DensityFunction, CompiledDensityFunctionImpl {

    @Shadow
    @Final
    private RegistryEntry<DensityFunction> function;

    @Unique
    private long pointer = 0L;

    @Unique
    private String errorMessage = null;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        final DensityFunction df = this.function.value();
        if (!DensityFunctionUtils.isCompiled(df)) {
            if (DensityFunctionUtils.DEBUG) {
                this.errorMessage = DensityFunctionUtils.getErrorMessage(
                        this,
                        ImmutableMap.of("function_value", df)
                );
                assert this.errorMessage != null;
                System.err.println("Failed to pass-through density function: registry_entry %s".formatted(this));
                System.err.println(DensityFunctionUtils.indent(this.errorMessage, false));
            }
            return;
        }

        this.pointer = ((CompiledDensityFunctionImpl) df).getDFIPointer();
        // memory management not needed
    }

    /**
     * @author ishland
     * @reason use native method
     */
    @Overwrite
    public double sample(DensityFunction.NoisePos pos) {
        return this.function.value().sample(pos);
    }

    /**
     * @author ishland
     * @reason use native method
     */
    @Overwrite
    public void method_40470(double[] ds, DensityFunction.class_6911 arg) {
        this.function.value().method_40470(ds, arg);
    }

    @Override
    public long getDFIPointer() {
        return this.pointer;
    }

    @Nullable
    @Override
    public String getCompilationFailedReason() {
        return this.errorMessage;
    }

    @Override
    public Type getDFIType() {
        return Type.PASS_THROUGH;
    }
}
