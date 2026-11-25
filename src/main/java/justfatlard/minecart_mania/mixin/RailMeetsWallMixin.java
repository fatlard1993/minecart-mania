package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.rail.MountedRails;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A floor rail laid under the foot of a climb becomes the bend up onto the wall, and wall and
 * ceiling rails beside a new floor rail turn to meet it, the way floor rails turn to meet
 * each other. Vanilla's own shaping only looks at floor rails.
 */
@Mixin(BaseRailBlock.class)
public abstract class RailMeetsWallMixin {
	@Inject(method = "onPlace", at = @At("TAIL"))
	private void minecartMania$meetMountedTrack(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
		if (movedByPiston || oldState.is(state.getBlock()) || !(level instanceof ServerLevel server)) return;
		MountedRails.reshape(server, pos);
		MountedRails.joinNeighbours(server, pos);
	}
}
