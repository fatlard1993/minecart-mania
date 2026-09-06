package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.rail.RailPins;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rail somebody set by hand keeps the shape they gave it.
 *
 * <p>Vanilla reshapes a rail every time a neighbour changes, which is how a rail laid beside
 * two others curves toward the one you did not mean. A pinned rail skips that, unless redstone
 * is on it: a powered curve is a switch, and a switch answers the lever.
 */
@Mixin(BaseRailBlock.class)
public abstract class RailPinMixin {
	@Inject(method = "updateDir", at = @At("HEAD"), cancellable = true)
	private void minecartMania$keepPinnedShape(Level level, BlockPos pos, BlockState state, boolean alwaysPlace,
			CallbackInfoReturnable<BlockState> cir) {
		if (level instanceof ServerLevel server && RailPins.isPinned(server, pos) && !level.hasNeighborSignal(pos)) {
			cir.setReturnValue(state);
		}
	}
}
