package com.ishland.c2me.fixes.worldgen.threading_issues.mixin.threading;

import com.ishland.c2me.fixes.worldgen.threading_issues.common.XPieceDataExtension;
import net.minecraft.structure.NetherFortressGenerator;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetherFortressGenerator.PieceData.class)
public class MixinNetherFortressGeneratorPieceData implements XPieceDataExtension {

    @Unique
    private final ThreadLocal<Integer> generatedCountThreadLocal = ThreadLocal.withInitial(() -> 0);

    @Dynamic
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/structure/NetherFortressGenerator$PieceData;generatedCount:I", opcode = Opcodes.GETFIELD))
    private int redirectGetGeneratedCount(NetherFortressGenerator.PieceData pieceData) {
        return this.generatedCountThreadLocal.get();
    }

    @SuppressWarnings("MixinAnnotationTarget")
    @Dynamic
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/structure/NetherFortressGenerator$PieceData;generatedCount:I", opcode = Opcodes.PUTFIELD), require = 0, expect = 0)
    private void redirectSetGeneratedCount(NetherFortressGenerator.PieceData pieceData, int value) {
        if (value == 0) {
            generatedCountThreadLocal.remove();
        } else {
            this.generatedCountThreadLocal.set(value);
        }
    }

    @Override
    public ThreadLocal<Integer> c2me$getGeneratedCountThreadLocal() {
        return this.generatedCountThreadLocal;
    }
}
