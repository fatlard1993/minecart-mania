package justfatlard.minecart_mania.cart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.minecart_mania.Main;
import justfatlard.pandorical.api.BlockEntry;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.RelPos;
import justfatlard.pandorical.api.StructurePose;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The chain between two carts, drawn only while it is doing something.
 *
 * <p>A slack chain hangs inside the gap and there is nothing to see; a stretched one is a
 * line of chain links from one cart to the other. That line is a Pandorical structure
 * anchored to the leading cart, rebuilt when its length or direction changes and merely moved
 * otherwise. Vanilla clients see the carts hold their distance and nothing between them.
 */
final class ChainVisual {
	private ChainVisual() {}

	private record Shape(int links, Direction.Axis axis) {}

	private static final Map<String, Shape> shown = new ConcurrentHashMap<>();
	/**
	 * Where the chain runs, from the cart's own origin, which is the middle of its underside:
	 * a quarter block down, so it hangs just under the floor of the cart where a coupling is.
	 */
	private static final double UNDER = -0.25;

	static void update(AbstractMinecart head, AbstractMinecart tail, double distance) {
		show(structureId(head.getUUID(), tail.getUUID()), head, tail.position().subtract(head.position()), distance);
	}

	/** The chain from a cart to the hand holding it. */
	static void leash(AbstractMinecart cart, Entity holder, double distance) {
		Vec3 hand = holder.position().add(0.0, holder.getBbHeight() * 0.6, 0.0);
		show(leashId(cart.getUUID()), cart, hand.subtract(cart.position()), distance);
	}

	static void hideLeash(UUID cart) {
		if (shown.containsKey(leashId(cart))) PandoricalApi.structures().setVisible(leashId(cart), false);
	}

	static void removeLeash(UUID cart) {
		if (shown.remove(leashId(cart)) != null) PandoricalApi.structures().despawn(leashId(cart));
	}

	/**
	 * A line of chain from the head along the gap, always: a chain that only showed when it
	 * was pulled taut left a train looking like carts that happened to keep their distance.
	 * At rest there is one link, the carts close enough that it reaches both.
	 *
	 * <p>The links are laid along the structure's own x and the whole thing is turned to face
	 * the far end by its pose, which the client eases between updates; so a chain swings round
	 * smoothly after a hand walking a circle. It used to be laid out block by block along the
	 * line and only re-laid when the count changed, which left it pointing wherever it had first
	 * pointed, hanging in the air, until the count ticked over and it jumped.
	 *
	 * <p>A block hangs off the origin by its corner, and a chain block runs its chain through
	 * the middle of the block, so the origin is set half a block to the side, turned with the
	 * rest, to put the chain on the line itself.
	 */
	private static void show(String id, Entity head, Vec3 gap, double distance) {
		boolean upright = Math.abs(gap.y) > gap.horizontalDistance();
		Shape want = new Shape(Math.max(1, (int) Math.ceil(distance) - 1), upright ? Direction.Axis.Y : Direction.Axis.X);
		float yaw = upright ? 0.0F : (float) Math.toDegrees(Math.atan2(gap.z, gap.x));
		double rad = Math.toRadians(yaw);
		double ox = upright ? -0.5 : 0.5 * Math.sin(rad);
		double oz = upright ? -0.5 : -0.5 * Math.cos(rad);
		StructurePose pose = new StructurePose(head.getX() + ox, head.getY() + UNDER, head.getZ() + oz, yaw);
		Shape have = shown.get(id);
		if (have == null || !have.equals(want)) {
			if (have != null) PandoricalApi.structures().despawn(id);
			PandoricalApi.structures().spawn(head, id, links(want, gap.y < 0), pose);
			shown.put(id, want);
		} else {
			PandoricalApi.structures().updatePose(id, pose);
			PandoricalApi.structures().setVisible(id, true);
		}
	}

	private static String leashId(UUID cart) {
		return Main.MOD_ID + ":leash/" + cart;
	}

	/** The chain between these two, whichever way round it was drawn. */
	static void remove(UUID one, UUID other) {
		for (String id : new String[] {structureId(one, other), structureId(other, one)}) {
			if (shown.remove(id) != null) PandoricalApi.structures().despawn(id);
		}
	}

	/** One chain per link, named for both ends: a cart in the middle of a train heads one and tails another. */
	private static String structureId(UUID head, UUID tail) {
		return Main.MOD_ID + ":chain/" + head + "/" + tail;
	}

	/** One chain block per block of gap, along the structure's x, or straight up or down. */
	private static List<BlockEntry> links(Shape shape, boolean downward) {
		BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, shape.axis());
		List<BlockEntry> out = new ArrayList<>();
		for (int i = 0; i < shape.links(); i++) {
			RelPos at = shape.axis() == Direction.Axis.Y ? new RelPos(0, downward ? -i : i, 0) : new RelPos(i, 0, 0);
			out.add(new BlockEntry(at, chain));
		}
		return out;
	}
}
