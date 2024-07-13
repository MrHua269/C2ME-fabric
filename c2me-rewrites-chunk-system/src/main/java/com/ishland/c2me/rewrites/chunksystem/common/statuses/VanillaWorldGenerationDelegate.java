package com.ishland.c2me.rewrites.chunksystem.common.statuses;

import com.ishland.c2me.base.common.scheduler.LockTokenImpl;
import com.ishland.c2me.base.common.scheduler.ScheduledTask;
import com.ishland.c2me.base.common.scheduler.SchedulingManager;
import com.ishland.c2me.base.mixin.access.IThreadedAnvilChunkStorage;
import com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext;
import com.ishland.c2me.rewrites.chunksystem.common.ChunkState;
import com.ishland.c2me.rewrites.chunksystem.common.NewChunkStatus;
import com.ishland.flowsched.executor.LockToken;
import com.ishland.flowsched.scheduler.ItemHolder;
import com.ishland.flowsched.scheduler.KeyStatusPair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.chunk.ChunkGenerationStep;
import net.minecraft.world.chunk.ChunkGenerationSteps;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.GenerationDependencies;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class VanillaWorldGenerationDelegate extends NewChunkStatus {

    private static KeyStatusPair<ChunkPos, ChunkState, ChunkLoadingContext>[] getDependencyFromStep(ChunkGenerationStep step) {
        ArrayList<KeyStatusPair<ChunkPos, ChunkState, ChunkLoadingContext>> deps = new ArrayList<>();
        final GenerationDependencies directDependencies = step.directDependencies();
        for (int x = -directDependencies.getMaxLevel(); x <= directDependencies.getMaxLevel(); x++) {
            for (int z = -directDependencies.getMaxLevel(); z <= directDependencies.getMaxLevel(); z++) {
                if (x == 0 && z == 0) continue;
                final KeyStatusPair<ChunkPos, ChunkState, ChunkLoadingContext> dep =
                        new KeyStatusPair<>(
                                new ChunkPos(x, z), fromVanillaStatus(directDependencies.get(Math.max(Math.abs(x), Math.abs(z))))
                        );
                deps.add(dep);
            }
        }

        return deps.toArray(KeyStatusPair[]::new);
    }

    private static <T> CompletableFuture<T> runTaskWithLock(ChunkPos target, int radius, SchedulingManager schedulingManager, Supplier<CompletableFuture<T>> action) {
        ObjectArrayList<LockToken> lockTargets = new ObjectArrayList<>((2 * radius + 1) * (2 * radius + 1) + 1);
        for (int x = target.x - radius; x <= target.x + radius; x++)
            for (int z = target.z - radius; z <= target.z + radius; z++)
                lockTargets.add(new LockTokenImpl(schedulingManager.getId(), ChunkPos.toLong(x, z), LockTokenImpl.Usage.WORLDGEN));

        final ScheduledTask<T> task = new ScheduledTask<>(
                target.toLong(),
                action,
                lockTargets.toArray(LockToken[]::new));
        schedulingManager.enqueue(task);
        return task.getFuture();
    }

    private final ChunkStatus status;
    private final KeyStatusPair<ChunkPos, ChunkState, ChunkLoadingContext>[] genDeps;
    private final KeyStatusPair<ChunkPos, ChunkState, ChunkLoadingContext>[] loadDeps;

    public VanillaWorldGenerationDelegate(int ordinal, ChunkStatus status) {
        super(ordinal, status);
        this.status = status;
        final ChunkGenerationStep genStep = ChunkGenerationSteps.GENERATION.get(status);
        final ChunkGenerationStep loadStep = ChunkGenerationSteps.LOADING.get(status);
        this.genDeps = getDependencyFromStep(genStep);
        this.loadDeps = getDependencyFromStep(loadStep);
    }

    @Override
    public CompletionStage<Void> upgradeToThis(ChunkLoadingContext context) {
        final ChunkGenerationContext chunkGenerationContext = ((IThreadedAnvilChunkStorage) context.tacs()).getGenerationContext();
        Chunk chunk = context.holder().getItem().get().chunk();
        if (chunk.getStatus().isAtLeast(status)) {
            return ChunkGenerationSteps.LOADING.get(status)
                    .run(((IThreadedAnvilChunkStorage) context.tacs()).getGenerationContext(), context.chunks(), chunk)
                    .whenComplete((chunk1, throwable) -> {
                        if (chunk1 != null) {
                            context.holder().getItem().set(new ChunkState(chunk1));
                        }
                    }).thenAccept(__ -> {});
        } else {
            final ChunkGenerationStep step = ChunkGenerationSteps.GENERATION.get(status);

            return runTaskWithLock(chunk.getPos(), step.blockStateWriteRadius(), context.schedulingManager(),
                    () -> step.run(chunkGenerationContext, context.chunks(), chunk)
                            .whenComplete((chunk1, throwable) -> {
                                if (chunk1 != null) {
                                    context.holder().getItem().set(new ChunkState(chunk1));
                                }
                            }).thenAccept(__ -> {})
            );
        }
    }

    @Override
    public CompletionStage<Void> downgradeFromThis(ChunkLoadingContext context) {
        return CompletableFuture.completedStage(null);
    }

    @Override
    public KeyStatusPair<ChunkPos, ChunkState, ChunkLoadingContext>[] getRelativeDependencies(ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, ?> holder) {
        final Chunk chunk = holder.getItem().get().chunk();
        if (chunk == null) return genDeps;
        if (chunk.getStatus().isAtLeast(status)) {
            return loadDeps;
        } else {
            return genDeps;
        }
    }

    @Override
    public String toString() {
        return "VanillaWorldGenerationDelegate{" +
               "status=" + status +
               '}';
    }
}