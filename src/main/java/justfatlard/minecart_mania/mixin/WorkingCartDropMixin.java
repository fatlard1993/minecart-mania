package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.WorkingCarts;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A working cart is its own item when broken or picked.
 *
 * <p>It is a chest cart underneath, and a chest cart drops a chest cart: break a dropper cart
 * and what came back was a plain chest cart, which placed as a plain chest cart, the work and
 * the dropper both gone. It comes back as what it was.
 */
@Mixin(MinecartChest.class)
public abstract class WorkingCartDropMixin {
	@Inject(method = "getDropItem", at = @At("HEAD"), cancellable = true)
	private void minecartMania$dropAsWorking(CallbackInfoReturnable<Item> cir) {
		Item item = WorkingCarts.itemOf((MinecartChest) (Object) this);
		if (item != null) cir.setReturnValue(item);
	}

	@Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
	private void minecartMania$pickAsWorking(CallbackInfoReturnable<ItemStack> cir) {
		Item item = WorkingCarts.itemOf((MinecartChest) (Object) this);
		if (item != null) cir.setReturnValue(new ItemStack(item));
	}
}
