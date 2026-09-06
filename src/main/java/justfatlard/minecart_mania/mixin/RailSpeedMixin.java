package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.rail.Rails;
import justfatlard.minecart_mania.rail.SwitchingRailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The track sets the pace, and the track can change under the cart.
 *
 * <p>Three things, all on the new movement code. The speed cap is read from the rail the cart
 * is on instead of the world's one number. Vanilla asks "is this the gold powered rail" by
 * identity before it boosts or brakes, and that question is widened to any powered rail so the
 * copper one pushes too. And every block the cart steps onto during a tick is read through
 * {@link #minecartMania$switchUnderCart}, which lets a crossing or a junction set its own shape
 * for the cart about to cross it: the cart's own stepping loop is the only place that knows
 * which block is next, so the switch has to happen inside it.
 */
@Mixin(NewMinecartBehavior.class)
public abstract class RailSpeedMixin extends MinecartBehavior {
	protected RailSpeedMixin(AbstractMinecart minecart) {
		super(minecart);
	}

	@Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
	private void minecartMania$railSpeed(ServerLevel level, CallbackInfoReturnable<Double> cir) {
		BlockPos pos = minecart.getCurrentBlockPosOrRailBelow();
		BlockState state = level.getBlockState(pos);
		if (!Rails.isRail(state)) return;
		double perSecond = Rails.maxSpeed(state);
		if (minecart.isInWater()) perSecond *= 0.5;
		cir.setReturnValue(perSecond / 20.0);
	}

	@Redirect(method = "calculateBoostTrackSpeed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
	private boolean minecartMania$anyPoweredRailBoosts(BlockState state, Object block) {
		return state.is((Block) block) || state.getBlock() instanceof PoweredRailBlock;
	}

	@Redirect(method = "calculateHaltTrackSpeed",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
	private boolean minecartMania$anyPoweredRailHalts(BlockState state, Object block) {
		return state.is((Block) block) || state.getBlock() instanceof PoweredRailBlock;
	}

	@Redirect(method = "moveAlongTrack",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState minecartMania$switchUnderCart(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof SwitchingRailBlock rail) {
			RailShape want = rail.shapeFor(state, minecart.getDeltaMovement());
			if (want != null && state.getValue(rail.getShapeProperty()) != want) {
				state = state.setValue(rail.getShapeProperty(), want);
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
		}
		return state;
	}
}
