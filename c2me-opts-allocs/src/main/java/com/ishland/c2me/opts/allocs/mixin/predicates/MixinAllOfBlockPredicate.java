package com.ishland.c2me.opts.allocs.mixin.predicates;

import com.ishland.c2me.opts.allocs.common.ducks.CombinedBlockPredicateExtension;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.blockpredicate.AllOfBlockPredicate;
import net.minecraft.world.gen.blockpredicate.BlockPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(AllOfBlockPredicate.class)
public abstract class MixinAllOfBlockPredicate implements CombinedBlockPredicateExtension {

    /**
     * @author ishland
     * @reason reduce alloc
     */
    @Overwrite
    public boolean test(StructureWorldAccess structureWorldAccess, BlockPos blockPos) {
        for (BlockPredicate blockPredicate : this.c2me$getPredicatesArray()) {
            if (!blockPredicate.test(structureWorldAccess, blockPos)) {
                return false;
            }
        }

        return true;
    }

}