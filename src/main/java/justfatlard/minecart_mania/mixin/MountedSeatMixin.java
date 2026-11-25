package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.SurfaceRide;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rider sits on the cart's top wherever its top faces: under a ceiling they hang below it, on
 * a climb they stand out from the wall. Left upright, a rider under a ceiling would have their
 * head in the block above and choke.
 *
 * <p>The rider's middle is put where an upright rider's middle would be, turned with the cart
 * about the cart's own middle; the rider's box stays upright around that point.
 */
@Mixin(AbstractMinecart.class)
public abstract class MountedSeatMixin {
	private static final double CART_HALF_HEIGHT = 0.35;

	@Inject(method = "getPassengerAttachmentPoint", at = @At("RETURN"), cancellable = true)
	private void minecartMania$seatAlongTop(Entity passenger, EntityDimensions dimensions, float scale, CallbackInfoReturnable<Vec3> cir) {
		AbstractMinecart cart = (AbstractMinecart) (Object) this;
		if (!(cart.level() instanceof ServerLevel)) return;
		Vec3 up = SurfaceRide.up(cart);
		if (up == null) return;
		double half = passenger.getBbHeight() / 2;
		Vec3 upright = cir.getReturnValue();
		Vec3 middle = new Vec3(upright.x, CART_HALF_HEIGHT, upright.z).add(up.scale(upright.y - CART_HALF_HEIGHT + half));
		cir.setReturnValue(middle.subtract(0, half, 0));
	}
}
