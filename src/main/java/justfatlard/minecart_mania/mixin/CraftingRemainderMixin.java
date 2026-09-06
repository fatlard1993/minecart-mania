package justfatlard.minecart_mania.mixin;

import justfatlard.minecart_mania.cart.SplitCarts;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every cart-with-a-block leaves a bare cart in the grid when it is used up. */
@Mixin(Item.class)
public abstract class CraftingRemainderMixin {
	@Inject(method = "getCraftingRemainder", at = @At("RETURN"), cancellable = true)
	private void minecartMania$cartComesBack(CallbackInfoReturnable<ItemStackTemplate> cir) {
		if (cir.getReturnValue() == null && SplitCarts.splits((Item) (Object) this)) {
			cir.setReturnValue(new ItemStackTemplate(Items.MINECART));
		}
	}
}
