package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.SurfaceRide;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A cart on wall or ceiling track is driven by {@link SurfaceRide} for the whole tick, before
 * vanilla's movement can look for a floor rail under it and, finding none, let it fall.
 */
@Mixin(NewMinecartBehavior.class)
public abstract class SurfaceRideMixin extends MinecartBehavior {
	protected SurfaceRideMixin(AbstractMinecart minecart) {
		super(minecart);
	}

	@Inject(method = "moveAlongTrack", at = @At("HEAD"), cancellable = true)
	private void minecartMania$rideMountedTrack(ServerLevel level, CallbackInfo ci) {
		if (SurfaceRide.continueRide(minecart, level)) ci.cancel();
	}
}
