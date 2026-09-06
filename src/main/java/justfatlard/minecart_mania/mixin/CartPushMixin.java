package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.CartCollisions;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Around vanilla's push of whatever the cart runs into: remember the speed, then make the hit count. */
@Mixin(NewMinecartBehavior.class)
public abstract class CartPushMixin extends MinecartBehavior {
	protected CartPushMixin(AbstractMinecart minecart) {
		super(minecart);
	}

	@Unique
	private Vec3 minecartMania$before = Vec3.ZERO;

	@Inject(method = "pushAndPickupEntities", at = @At("HEAD"))
	private void minecartMania$rememberSpeed(CallbackInfoReturnable<Boolean> cir) {
		minecartMania$before = minecart.getDeltaMovement();
	}

	@Inject(method = "pushAndPickupEntities", at = @At("RETURN"))
	private void minecartMania$strike(CallbackInfoReturnable<Boolean> cir) {
		CartCollisions.afterPush(minecart, minecartMania$before);
	}
}
