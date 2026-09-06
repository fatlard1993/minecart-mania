package justfatlard.minecart_mania.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rail stands on nothing. Bridges, trestles and the span over a ravine are the whole of what
 * a railway is for, and a track that needs a block under every sleeper cannot build any of them.
 *
 * <p>Two doors to shut, not one. Placement asks {@code canSurvive}; a neighbour changing asks a
 * private {@code shouldBeRemoved} that looks at the ground for itself and never consults the
 * first. With only the first held open, one floating rail stood and the second one laid beside
 * it took both down.
 */
@Mixin(BaseRailBlock.class)
public abstract class RailSupportMixin {
	@Inject(method = "shouldBeRemoved", at = @At("HEAD"), cancellable = true)
	private static void minecartMania$staysUp(BlockPos pos, Level level, RailShape shape, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}

	@Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
	private void minecartMania$standsAlone(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}
}
