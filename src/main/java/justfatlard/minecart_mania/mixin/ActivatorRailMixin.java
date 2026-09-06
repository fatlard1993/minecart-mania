package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.FurnaceControls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A powered activator rail is a switch for a furnace cart, as it already is a trigger for a TNT one. */
@Mixin(AbstractMinecart.class)
public abstract class ActivatorRailMixin {
	@Inject(method = "activateMinecart", at = @At("HEAD"))
	private void minecartMania$throwSwitch(ServerLevel level, int x, int y, int z, boolean powered, CallbackInfo ci) {
		FurnaceControls.activate((AbstractMinecart) (Object) this, powered);
	}
}
