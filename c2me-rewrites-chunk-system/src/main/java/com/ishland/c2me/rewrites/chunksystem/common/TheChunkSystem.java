package com.ishland.c2me.rewrites.chunksystem.common;

import com.ishland.c2me.base.common.GlobalExecutors;
import com.ishland.c2me.base.common.scheduler.SchedulingManager;
import com.ishland.c2me.base.mixin.access.IThreadedAnvilChunkStorage;
import com.ishland.flowsched.scheduler.DaemonizedStatusAdvancingScheduler;
import com.ishland.flowsched.scheduler.ItemHolder;
import com.ishland.flowsched.scheduler.ItemStatus;
import com.ishland.flowsched.scheduler.KeyStatusPair;
import com.ishland.flowsched.util.Assertions;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.util.math.ChunkPos;

import java.util.concurrent.ThreadFactory;

public class TheChunkSystem extends DaemonizedStatusAdvancingScheduler<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> {

    private final Long2IntMap managedTickets = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
    private final SchedulingManager schedulingManager = new SchedulingManager(GlobalExecutors.asyncScheduler);
    private final ServerChunkLoadingManager tacs;

    public TheChunkSystem(ThreadFactory threadFactory, ServerChunkLoadingManager tacs) {
        super(threadFactory);
        this.tacs = tacs;
        managedTickets.defaultReturnValue(NewChunkStatus.vanillaLevelToStatus.length - 1);
    }

    @Override
    protected ItemStatus<ChunkPos, ChunkState, ChunkLoadingContext> getUnloadedStatus() {
        return NewChunkStatus.NEW;
    }

    @Override
    protected ChunkLoadingContext makeContext(ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder, ItemStatus<ChunkPos, ChunkState, ChunkLoadingContext> nextStatus, KeyStatusPair<ChunkPos, ChunkState, ChunkLoadingContext>[] dependencies, boolean isUpgrade) {
        Assertions.assertTrue(nextStatus instanceof NewChunkStatus);
        final NewChunkStatus nextStatus1 = (NewChunkStatus) nextStatus;

        int radius;
        if (dependencies.length == 0) {
            radius = 0;
        } else {
            int actualDependencies = dependencies.length + 1;
            radius = (int) ((Math.sqrt(actualDependencies) - 1) / 2);
            Assertions.assertTrue((radius * 2 + 1) * (radius * 2 + 1) == actualDependencies);
        }

        return new ChunkLoadingContext(holder, this.tacs, this.schedulingManager, BoundedRegionArray.create(holder.getKey().x, holder.getKey().z, radius,
                (x, z) -> this.getHolder(new ChunkPos(x, z)).getUserData().get()), dependencies);
    }

    @Override
    protected void onItemCreation(ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder) {
        super.onItemCreation(holder);
        holder.getUserData().set(new NewChunkHolderVanillaInterface(holder, ((IThreadedAnvilChunkStorage) this.tacs).getWorld(), ((IThreadedAnvilChunkStorage) this.tacs).getLightingProvider(), this.tacs));
    }

    @Override
    protected void onItemRemoval(ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder) {
        super.onItemRemoval(holder);
        final WorldGenerationProgressListener listener = ((IThreadedAnvilChunkStorage) this.tacs).getWorldGenerationProgressListener();
        if (listener != null) {
            listener.setChunkStatus(holder.getKey(), null);
        }
    }

    @Override
    protected void onItemUpgrade(ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder, ItemStatus<ChunkPos, ChunkState, ChunkLoadingContext> statusReached) {
        super.onItemUpgrade(holder, statusReached);
        final NewChunkStatus statusReached1 = (NewChunkStatus) statusReached;
        final NewChunkStatus prevStatus = (NewChunkStatus) statusReached.getPrev();
        final WorldGenerationProgressListener listener = ((IThreadedAnvilChunkStorage) this.tacs).getWorldGenerationProgressListener();
        if (listener != null && prevStatus.getEffectiveVanillaStatus() != statusReached1.getEffectiveVanillaStatus()) {
            listener.setChunkStatus(holder.getKey(), statusReached1.getEffectiveVanillaStatus());
        }
        if (prevStatus.toChunkLevelType() != statusReached1.toChunkLevelType()) {
            ((IThreadedAnvilChunkStorage) this.tacs).getMainThreadExecutor().execute(
                    () -> ((IThreadedAnvilChunkStorage) this.tacs).invokeOnChunkStatusChange(holder.getKey(), statusReached1.toChunkLevelType()));
        }
    }

    @Override
    protected void onItemDowngrade(ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder, ItemStatus<ChunkPos, ChunkState, ChunkLoadingContext> statusReached) {
        super.onItemDowngrade(holder, statusReached);
        final NewChunkStatus statusReached1 = (NewChunkStatus) statusReached;
        final NewChunkStatus prevStatus = (NewChunkStatus) statusReached.getNext();
        final WorldGenerationProgressListener listener = ((IThreadedAnvilChunkStorage) this.tacs).getWorldGenerationProgressListener();
        if (listener != null && prevStatus.getEffectiveVanillaStatus() != statusReached1.getEffectiveVanillaStatus()) {
            listener.setChunkStatus(holder.getKey(), statusReached1.getEffectiveVanillaStatus());
        }
        if (prevStatus.toChunkLevelType() != statusReached1.toChunkLevelType()) {
            ((IThreadedAnvilChunkStorage) this.tacs).getMainThreadExecutor().execute(
                    () -> ((IThreadedAnvilChunkStorage) this.tacs).invokeOnChunkStatusChange(holder.getKey(), statusReached1.toChunkLevelType()));
        }
    }

    public ChunkHolder vanillaIf$setLevel(long pos, int level) {
        this.schedulingManager.updatePriorityFromLevel(pos, level);
        Assertions.assertTrue(!Thread.holdsLock(this.managedTickets));
        synchronized (this.managedTickets) {
            final int oldLevel = this.managedTickets.put(pos, level);
            NewChunkStatus oldStatus = NewChunkStatus.fromVanillaLevel(oldLevel);
            NewChunkStatus newStatus = NewChunkStatus.fromVanillaLevel(level);
            if (oldStatus != newStatus) {
                ChunkHolder vanillaHolder;
                if (newStatus != this.getUnloadedStatus()) {
                    final ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder = this.addTicket(new ChunkPos(pos), newStatus, () -> {
                    });
                    vanillaHolder = holder.getUserData().get();
                } else {
                    this.managedTickets.remove(pos);
                    vanillaHolder = null;
                }
                if (oldStatus != this.getUnloadedStatus()) {
                    this.removeTicket(new ChunkPos(pos), oldStatus);
                }
                return vanillaHolder;
            } else {
                return null;
            }
        }
    }
}