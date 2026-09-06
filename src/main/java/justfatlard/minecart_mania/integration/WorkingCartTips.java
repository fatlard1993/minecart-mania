package justfatlard.minecart_mania.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.minecart_mania.cart.WorkingCarts;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The working carts introduced by name on the block tip.
 *
 * <p>They are chest carts underneath, and the tip names what it sees by the entity's own name,
 * which is "Minecart with Chest". Only loaded when block-tip is here: this class imports its
 * API, and a class that mentions a missing one is a class that cannot be loaded.
 */
public final class WorkingCartTips {
	private WorkingCartTips() {}

	public static void register() {
		BlockTipApi.nameEntity((entity, player) -> {
			if (!(entity instanceof AbstractMinecart cart)) return null;
			Item item = WorkingCarts.itemOf(cart);
			return item == null ? null : new ItemStack(item).getHoverName().getString();
		});
	}
}
