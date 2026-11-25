package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.SurfaceRide;
import justfatlard.minecart_mania.rail.MountedRails;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A minecart can be set down on wall and ceiling track, which vanilla refuses for not being in
 * the rails tag; it is set down halfway along the track, the right way up for where it is.
 */
@Mixin(MinecartItem.class)
public abstract class CartOnMountedTrackMixin {
	@Redirect(method = "useOn", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean minecartMania$mountedTrackTakesCarts(BlockState state, TagKey<Block> tag) {
		return state.is(tag) || MountedRails.isMounted(state);
	}

	@ModifyVariable(method = "useOn", at = @At("STORE"), ordinal = 0)
	private Vec3 minecartMania$setDownOnMountedTrack(Vec3 spawn, UseOnContext context) {
		BlockState state = context.getLevel().getBlockState(context.getClickedPos());
		return MountedRails.isMounted(state) ? SurfaceRide.restingSpot(state, context.getClickedPos()) : spawn;
	}
}
