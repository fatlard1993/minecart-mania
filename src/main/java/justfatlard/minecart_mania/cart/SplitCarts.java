package justfatlard.minecart_mania.cart;

import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A cart with a block in it comes apart on the crafting grid.
 *
 * <p>Vanilla lets you put a chest into a cart and never take it out again. The recipe for
 * that is one ingredient and one result, the block; the cart comes back as the crafting
 * remainder, the way a bucket does after the milk, which is the only way a grid hands back two
 * things for one.
 */
public final class SplitCarts {
	private SplitCarts() {}

	private static final Map<Item, Item> SPLITS = Map.of(
		Items.CHEST_MINECART, Items.CHEST,
		Items.FURNACE_MINECART, Items.FURNACE,
		Items.HOPPER_MINECART, Items.HOPPER,
		Items.TNT_MINECART, Items.TNT);

	public static boolean splits(Item item) {
		return SPLITS.containsKey(item) || WorkingCarts.isCartItem(item);
	}
}
