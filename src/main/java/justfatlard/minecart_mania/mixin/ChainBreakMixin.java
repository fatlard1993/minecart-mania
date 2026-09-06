package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.ChainLinks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chained cart that is destroyed lets go of its chain.
 *
 * <p>The partner's tick cannot tell a broken cart from one whose chunk went away: both are
 * simply not there any more. Only the cart itself knows why it is going, so this is where the
 * pair is parted and the chain dropped - for the reasons that destroy a cart, and not for an
 * unload, which is meant to be picked up again where it left off.
 */
@Mixin(Entity.class)
public abstract class ChainBreakMixin {
	@Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
	private void minecartMania$dropChain(Entity.RemovalReason reason, CallbackInfo ci) {
		if (!reason.shouldDestroy()) return;
		if (!((Object) this instanceof AbstractMinecart cart)) return;
		if (!(cart.level() instanceof ServerLevel level)) return;
		ChainLinks.broken(level, cart);
	}
}
