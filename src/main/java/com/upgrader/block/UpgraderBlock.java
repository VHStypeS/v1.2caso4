package com.upgrader.block;

import com.upgrader.service.Upgrading;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The Upgrader: right click opens the crate-upgrade screen (no inventory slots, everything is validated by the server). */
public class UpgraderBlock extends Block {
    public UpgraderBlock(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Upgrading.open(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
