package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.SurfaceRide;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where vanilla finds no floor rail under a cart and is about to let it roll free, wall or
 * ceiling track there takes it on instead: a cart coming off a floor rail onto the bend up a
 * wall, or one set down or loaded on mounted track.
 */
@Mixin(AbstractMinecart.class)
public abstract class SurfaceCatchMixin {
	@Inject(method = "comeOffTrack", at = @At("HEAD"), cancellable = true)
	private void minecartMania$catchOnMountedTrack(ServerLevel level, CallbackInfo ci) {
		if (SurfaceRide.catchCart((AbstractMinecart) (Object) this, level)) ci.cancel();
	}
}
