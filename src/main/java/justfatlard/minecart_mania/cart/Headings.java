package justfatlard.minecart_mania.cart;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

/**
 * Which way a cart is going, as a compass direction.
 *
 * <p>Not the cart's yaw: a minecart is the same shape both ways round and the game flips its
 * yaw freely, so "the way it faces" is a coin toss. The motion is the truth while it moves;
 * once it stops, the last direction it was seen moving is the best answer there is, and a
 * cart that has never moved falls back to the yaw.
 */
public final class Headings {
	private Headings() {}

	private static final Map<UUID, Direction> last = new ConcurrentHashMap<>();
	private static final double MOVING = 1.0E-4;

	public static Direction of(AbstractMinecart cart) {
		Vec3 m = cart.getDeltaMovement();
		if (m.horizontalDistanceSqr() > MOVING) {
			Direction heading = Math.abs(m.x) >= Math.abs(m.z)
				? (m.x > 0 ? Direction.EAST : Direction.WEST)
				: (m.z > 0 ? Direction.SOUTH : Direction.NORTH);
			last.put(cart.getUUID(), heading);
			return heading;
		}
		Direction remembered = last.get(cart.getUUID());
		if (remembered != null) return remembered;
		Direction yaw = cart.getMotionDirection();
		return yaw.getAxis().isVertical() ? Direction.NORTH : yaw;
	}

	/** Called every tick for every cart so the memory is fresh when the cart stops. */
	public static void note(AbstractMinecart cart) {
		of(cart);
	}
}
