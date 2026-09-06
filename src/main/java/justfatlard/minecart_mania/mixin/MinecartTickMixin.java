package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.ChainLinks;
import justfatlard.minecart_mania.cart.WorkingCarts;
import justfatlard.minecart_mania.cart.Handbrake;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server-side things a cart does every tick before it moves: brake, and mind its chain. */
@Mixin(AbstractMinecart.class)
public abstract class MinecartTickMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void minecartMania$beforeMove(CallbackInfo ci) {
		AbstractMinecart cart = (AbstractMinecart) (Object) this;
		if (cart.level().isClientSide()) return;
		justfatlard.minecart_mania.cart.Headings.note(cart);
		Handbrake.tick(cart);
		ChainLinks.tick(cart);
		WorkingCarts.tick(cart);
	}
}
