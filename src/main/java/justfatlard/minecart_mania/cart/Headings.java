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
		Direction remembered = last.get(cart.getUUID());

		// The rail says which line the cart is on; the motion only has to say which way along
		// it. A cart in a chain is tugged sideways by its partner, and read from the motion
		// alone a straight run could come out as a heading across the track.
		Direction.Axis axis = axisOf(railShape(cart));
		if (axis != null) {
			double along = axis == Direction.Axis.X ? m.x : m.z;
			if (Math.abs(along) > MOVING_ALONG) {
				Direction heading = Direction.fromAxisAndDirection(axis,
					along > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
				last.put(cart.getUUID(), heading);
				return heading;
			}
			if (remembered != null && remembered.getAxis() == axis) return remembered;
			Direction yaw = cart.getMotionDirection();
			return yaw.getAxis() == axis ? yaw : Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
		}

		if (m.horizontalDistanceSqr() > MOVING) {
			Direction heading = Math.abs(m.x) >= Math.abs(m.z)
				? (m.x > 0 ? Direction.EAST : Direction.WEST)
				: (m.z > 0 ? Direction.SOUTH : Direction.NORTH);
			last.put(cart.getUUID(), heading);
			return heading;
		}
		if (remembered != null) return remembered;
		Direction yaw = cart.getMotionDirection();
		return yaw.getAxis().isVertical() ? Direction.NORTH : yaw;
	}

	/** Motion along the rail's own line that counts as going somewhere. */
	private static final double MOVING_ALONG = 0.01;

	/** The shape of the rail the cart is on, or null off the rails. */
	public static net.minecraft.world.level.block.state.properties.RailShape railShape(AbstractMinecart cart) {
		return railShape(cart.level().getBlockState(cart.getCurrentBlockPosOrRailBelow()));
	}

	/** The shape of this block as a rail, or null when it is not one. */
	public static net.minecraft.world.level.block.state.properties.RailShape railShape(net.minecraft.world.level.block.state.BlockState state) {
		if (!(state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock rail)) return null;
		return state.getValue(rail.getShapeProperty());
	}

	/** The line a straight or sloped rail runs on; null for a curve or no rail. */
	public static Direction.Axis axisOf(net.minecraft.world.level.block.state.properties.RailShape shape) {
		if (shape == null) return null;
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> Direction.Axis.Z;
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> Direction.Axis.X;
			default -> null;
		};
	}

	/** The way a sloped rail rises, or null when it is flat. */
	public static Direction riseOf(net.minecraft.world.level.block.state.properties.RailShape shape) {
		if (shape == null) return null;
		return switch (shape) {
			case ASCENDING_NORTH -> Direction.NORTH;
			case ASCENDING_SOUTH -> Direction.SOUTH;
			case ASCENDING_EAST -> Direction.EAST;
			case ASCENDING_WEST -> Direction.WEST;
			default -> null;
		};
	}

	/** The way a cart is taken to be going, when something other than motion decides it. */
	public static void set(AbstractMinecart cart, Direction heading) {
		last.put(cart.getUUID(), heading);
	}

	/**
	 * A cart just set on a rail faces the way its placer does, along the rail.
	 *
	 * <p>The rail allows two ways and the player is looking more one of them than the other;
	 * that is the way. Off a straight rail the player's own facing stands.
	 */
	public static void place(AbstractMinecart cart, net.minecraft.world.entity.player.Player player) {
		Direction facing = player.getDirection();
		Direction.Axis axis = axisOf(railShape(cart.level().getBlockState(cart.blockPosition())));
		if (axis != null && facing.getAxis() != axis) {
			Vec3 look = player.getLookAngle();
			double along = axis == Direction.Axis.X ? look.x : look.z;
			facing = Direction.fromAxisAndDirection(axis,
				along >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
		}
		set(cart, facing);
		if (cart instanceof net.minecraft.world.entity.vehicle.minecart.MinecartFurnace furnace) {
			FurnaceControls.setHeading(furnace, facing);
		}
	}

	/** Called every tick for every cart so the memory is fresh when the cart stops. */
	public static void note(AbstractMinecart cart) {
		of(cart);
	}
}
