package justfatlard.minecart_mania.cart;

import com.mojang.math.Transformation;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.minecart_mania.Main;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An arrow over a cart that has a direction to tell.
 *
 * <p>A furnace cart is the same shape both ways round and a dropper cart works to one side of
 * itself, so which way either is set is invisible from across the room, and a train is a row of
 * carts you cannot read. The arrow lies flat over the cart: the furnace's points the way it
 * pushes, a working cart's points the way it lays or throws. It is an item display riding the
 * cart, so a vanilla client draws it, and it is redrawn from the cart every tick rather than
 * saved - one read back from disk is a stray and is dropped on sight.
 */
public final class DirectionMarkers {
	private DirectionMarkers() {}

	public static final String TAG = Main.MOD_ID + ":marker";

	private static final Map<UUID, Display.ItemDisplay> markers = new ConcurrentHashMap<>();
	private static final Map<UUID, Direction> shown = new ConcurrentHashMap<>();

	private static final float SCALE = 0.45F;
	/** How far over the cart's own origin the arrow lies: just above the rim. */
	private static final float ABOVE = 0.55F;
	/**
	 * The arrow item points from its bottom left to its top right, a quarter turn short of
	 * straight up; laid flat with its top to the heading it would point forty-five degrees off.
	 */
	private static final float SKEW = -45.0F;

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.ItemDisplay && entity.entityTags().contains(TAG)) entity.discard();
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof AbstractMinecart cart) drop(cart);
		});
	}

	/** Which way this cart's arrow points, or null for a cart with nothing to say. */
	private static Direction pointing(AbstractMinecart cart) {
		if (cart instanceof MinecartFurnace) return Headings.of(cart);
		if (cart instanceof MinecartChest chest) {
			WorkingCarts.Work work = WorkingCarts.workOf(chest);
			if (work != null) return work.side().of(Headings.of(cart));
		}
		return null;
	}

	/** Once a tick, server side: the arrow is there, rides along, and points the right way. */
	public static void tick(AbstractMinecart cart) {
		if (!(cart.level() instanceof ServerLevel level)) return;
		Direction dir = pointing(cart);
		if (dir == null) return;

		Display.ItemDisplay marker = markers.get(cart.getUUID());
		if (marker == null || marker.isRemoved() || marker.level() != level) {
			marker = spawn(level, cart, dir);
			markers.put(cart.getUUID(), marker);
			shown.put(cart.getUUID(), dir);
		} else if (shown.get(cart.getUUID()) != dir) {
			marker.setTransformation(pose(dir));
			shown.put(cart.getUUID(), dir);
		}
		// Riding is the smooth way to follow; a cart that would not take a passenger has the
		// arrow walked after it instead.
		if (marker.getVehicle() != cart) marker.setPos(cart.getX(), cart.getY(), cart.getZ());
	}

	/** The arrow goes with the cart. */
	public static void drop(AbstractMinecart cart) {
		shown.remove(cart.getUUID());
		Display.ItemDisplay marker = markers.remove(cart.getUUID());
		if (marker != null) marker.discard();
	}

	private static Display.ItemDisplay spawn(ServerLevel level, AbstractMinecart cart, Direction dir) {
		Display.ItemDisplay marker = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		marker.setPos(cart.getX(), cart.getY(), cart.getZ());
		marker.getSlot(0).set(new ItemStack(Items.ARROW));
		marker.setItemTransform(ItemDisplayContext.NONE);
		marker.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		marker.setPosRotInterpolationDuration(1);
		marker.setTransformationInterpolationDuration(3);
		marker.setTransformation(pose(dir));
		marker.addTag(TAG);
		level.addFreshEntity(marker);
		marker.startRiding(cart, true, true);
		return marker;
	}

	/** Face up over the cart, tip to the direction. */
	private static Transformation pose(Direction dir) {
		Quaternionf turn = new Quaternionf()
			.rotateY((float) Math.toRadians(-dir.toYRot() + SKEW))
			.rotateX((float) Math.toRadians(90));
		return new Transformation(new Vector3f(0, ABOVE, 0), turn, new Vector3f(SCALE, SCALE, SCALE), null);
	}
}
