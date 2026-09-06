package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.ChainLinks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chained carts neither bounce off nor block each other.
 *
 * <p>The new movement makes carts solid to one another, so a follower catching its leader
 * rams it and both stop dead; and the older shove is an elastic collision on top. Both are
 * right for strangers and wrong for a train: the chain is what holds the pair's distance,
 * and everything else the game does when they touch works against it.
 */
@Mixin(AbstractMinecart.class)
public abstract class ChainPushMixin {
	@Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
	private void minecartMania$passPartner(Entity other, CallbackInfoReturnable<Boolean> cir) {
		if (!(other instanceof AbstractMinecart cart)) return;
		if (ChainLinks.linked((AbstractMinecart) (Object) this, cart)) cir.setReturnValue(false);
	}

	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void minecartMania$noBounceInTrain(Entity other, CallbackInfo ci) {
		if (!(other instanceof AbstractMinecart cart)) return;
		if (ChainLinks.linked((AbstractMinecart) (Object) this, cart)) ci.cancel();
	}
}
