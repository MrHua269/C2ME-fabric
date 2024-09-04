package com.ishland.c2me.opts.dfc.common.gen;

import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.vif.EachApplierVanillaInterface;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class CompiledDensityFunction implements DensityFunction {

    private final CompiledEntry compiledEntry;
    private final DensityFunction blendingFallback;

    public CompiledDensityFunction(CompiledEntry compiledEntry, DensityFunction blendingFallback) {
        this.compiledEntry = Objects.requireNonNull(compiledEntry);
        this.blendingFallback = blendingFallback;
    }

    @Override
    public double sample(NoisePos pos) {
        if (pos.getBlender() != Blender.getNoBlending()) {
            if (this.blendingFallback == null) {
                throw new IllegalStateException("blendingFallback is no more");
            }
            return this.blendingFallback.sample(pos);
        } else {
            return this.compiledEntry.evalSingle(pos.blockX(), pos.blockY(), pos.blockZ(), EvalType.from(pos));
        }
    }

    @Override
    public void fill(double[] densities, EachApplier applier) {
        if (applier instanceof ChunkNoiseSampler sampler) {
            if (sampler.getBlender() != Blender.getNoBlending()) {
                if (this.blendingFallback == null) {
                    throw new IllegalStateException("blendingFallback is no more");
                }
                this.blendingFallback.fill(densities, applier);
                return;
            }
        }
        if (applier instanceof EachApplierVanillaInterface vanillaInterface) {
            this.compiledEntry.evalMulti(densities, vanillaInterface.getX(), vanillaInterface.getY(), vanillaInterface.getZ(), EvalType.from(applier));
            return;
        }

        int[] x = new int[densities.length];
        int[] y = new int[densities.length];
        int[] z = new int[densities.length];
        for (int i = 0; i < densities.length; i ++) {
            NoisePos pos = applier.at(i);
            x[i] = pos.blockX();
            y[i] = pos.blockY();
            z[i] = pos.blockZ();
        }
        this.compiledEntry.evalMulti(densities, x, y, z, EvalType.from(applier));
    }

    @Override
    public DensityFunction apply(DensityFunctionVisitor visitor) {
        boolean modified = false;
        List<Object> args = this.compiledEntry.getArgs();
        ListIterator<Object> iterator = args.listIterator();
        while (iterator.hasNext()) {
            Object next = iterator.next();
            if (next instanceof DensityFunction df) {
                DensityFunction applied = df.apply(visitor);
                if (df != applied) {
                    iterator.set(applied);
                    modified = true;
                }
            }
        }
        DensityFunction fallback = this.blendingFallback.apply(visitor);
        if (fallback != this.blendingFallback) {
            modified = true;
        }
        if (modified) {
            return new CompiledDensityFunction(this.compiledEntry.newInstance(args), fallback);
        } else {
            return this;
        }
    }

    @Override
    public double minValue() {
        return this.blendingFallback.minValue();
    }

    @Override
    public double maxValue() {
        return this.blendingFallback.maxValue();
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        throw new UnsupportedOperationException();
    }
}