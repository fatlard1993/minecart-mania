package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.WorkingCarts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A working cart opens its own screen; sneak to get the plain chest underneath.
 *
 * <p>Hung on the chest cart itself: it answers a click on its own now, without going through
 * the container cart above it, and a hook on the parent was left waiting for a call that
 * never came, the working carts opening as bare chests.
 */
@Mixin(MinecartChest.class)
public abstract class ContainerCartMixin {
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void minecartMania$workScreen(Player player, InteractionHand hand, Vec3 hit, CallbackInfoReturnable<InteractionResult> cir) {
		MinecartChest cart = (MinecartChest) (Object) this;
		if (player.isShiftKeyDown() || WorkingCarts.workOf(cart) == null) return;
		if (player instanceof ServerPlayer server) WorkingCarts.openScreen(server, cart);
		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
