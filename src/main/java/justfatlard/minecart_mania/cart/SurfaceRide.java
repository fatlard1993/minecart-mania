package justfatlard.minecart_mania.cart;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.minecart_mania.rail.MountedRailBlock;
import justfatlard.minecart_mania.rail.MountedRails;
import justfatlard.minecart_mania.rail.Rails;
import justfatlard.minecart_mania.rail.TrackPath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Carts on wall and ceiling track.
 *
 * <p>Vanilla's movement only rolls along a floor: it budgets a tick's travel in horizontal
 * distance and works out height from the slope. Up a wall there is no horizontal distance and
 * under a ceiling there is no falling onto the rail, so on mounted track the cart is driven here
 * instead: as a speed along the block's path, with gravity pulling along the path, so a climb
 * slows it and a drop speeds it, and level track on a wall or a ceiling neither. A cart never
 * falls off mounted track: under a ceiling it holds on at any speed, and one that stalls on a
 * climb rolls back down.
 *
 * <p>A cart is taken on when vanilla finds no floor rail under it and there is mounted track
 * where it is, which is how a cart rolling off a floor rail onto the bend up a wall comes in.
 * It is handed back on reaching a floor rail, moving along it. At the end of a line it stops,
 * as at buffers.
 *
 * <p>Whatever else changes the cart's motion in its tick (the handbrake, a chain, a furnace) has
 * done so before this runs; the part of that motion along the track is the cart's speed.
 */
public final class SurfaceRide {
	private SurfaceRide() {}

	/** Where along which block's path a cart is. */
	private record Ride(BlockPos block, double t) {}

	private static final Map<UUID, Ride> RIDES = new ConcurrentHashMap<>();

	/** How far a cart's middle stands off the track, the same as vanilla's cart above a floor rail. */
	private static final double STANDOFF = 0.45;
	/** Half the cart's height: its position is its bottom, its middle is this far above. */
	private static final double HALF_HEIGHT = 0.35;
	/** How near a cart's middle must be to mounted track for the track to take it on. */
	private static final double CATCH = 0.3;
	/** A powered rail's push per tick, vanilla's. */
	private static final double BOOST = 0.06;
	/** Below this a braking powered rail holds a cart still, enough to hold it against gravity on a climb. */
	private static final double HOLD = 0.06;

	public static boolean isRiding(AbstractMinecart cart) {
		return RIDES.containsKey(cart.getUUID());
	}

	/** Where a cart set down on this block of mounted track stands: halfway along it. */
	public static Vec3 restingSpot(BlockState state, BlockPos pos) {
		TrackPath path = ((MountedRailBlock) state.getBlock()).path(state).offset(pos);
		return middleAt(path, 0.5).subtract(0, HALF_HEIGHT, 0);
	}

	/** Which way the top of a cart on mounted track faces, or null if it is not on any. */
	public static @Nullable Vec3 up(AbstractMinecart cart) {
		Ride ride = RIDES.get(cart.getUUID());
		if (ride == null) return null;
		BlockState state = cart.level().getBlockState(ride.block());
		if (!(state.getBlock() instanceof MountedRailBlock mounted)) return null;
		return mounted.path(state).up(ride.t());
	}

	/** Drive a cart already on mounted track for this tick; false if it is not on any. */
	public static boolean continueRide(AbstractMinecart cart, ServerLevel level) {
		Ride ride = RIDES.get(cart.getUUID());
		if (ride == null) return false;
		if (!MountedRails.isMounted(level.getBlockState(ride.block()))) {
			RIDES.remove(cart.getUUID());
			return false;
		}
		drive(cart, level, ride);
		return true;
	}

	/** Take a cart vanilla has found off its rails onto mounted track, if there is some where it is. */
	public static boolean catchCart(AbstractMinecart cart, ServerLevel level) {
		if (continueRide(cart, level)) return true;
		Ride ride = locate(cart, level);
		if (ride == null) return false;
		drive(cart, level, ride);
		return true;
	}

	/** A cart gone, broken or unloaded, is let go; one loaded again finds its place from where it stands. */
	public static void register() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> RIDES.remove(entity.getUUID()));
	}

	public static void clear() {
		RIDES.clear();
	}

	/** The mounted track the cart is on, found from where it stands: the block it is in, or the one its middle is in. */
	private static @Nullable Ride locate(AbstractMinecart cart, ServerLevel level) {
		Vec3 middle = cart.position().add(0, HALF_HEIGHT, 0);
		Ride best = null;
		double bestDistance = CATCH * CATCH;
		for (BlockPos pos : new BlockPos[] {cart.blockPosition(), BlockPos.containing(middle)}) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof MountedRailBlock mounted)) continue;
			TrackPath path = mounted.path(state).offset(pos);
			for (int i = 0; i <= 20; i++) {
				double t = i / 20.0;
				double distance = middleAt(path, t).distanceToSqr(middle);
				if (distance < bestDistance) {
					best = new Ride(pos.immutable(), t);
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private static Vec3 middleAt(TrackPath path, double t) {
		return path.at(t).add(path.up(t).scale(STANDOFF));
	}

	private static void drive(AbstractMinecart cart, ServerLevel level, Ride ride) {
		BlockPos block = ride.block();
		double t = ride.t();
		BlockState state = level.getBlockState(block);
		TrackPath path = ((MountedRailBlock) state.getBlock()).path(state).offset(block);

		// The speed is the motion along the track: gravity, which vanilla has already added this
		// tick, and whatever the cart's other systems did to it, less what points off the track.
		double speed = cart.getDeltaMovement().dot(path.tangent(t));
		speed *= cart.getBehavior().getSlowdownFactor() * (cart.isInWater() ? 0.95 : 1.0);
		if (state.hasProperty(MountedRailBlock.POWERED)) {
			if (state.getValue(MountedRailBlock.POWERED)) {
				// From a standstill a powered rail pushes uphill, which is what makes a powered climb
				// a lift; on the level it has no way to choose, so it waits for a nudge. On a climb a
				// cart at rest has already picked up a tick of gravity, so up to that much is rest.
				double rise = path.tangent(t).y;
				boolean climb = Math.abs(rise) > 0.1;
				if (Math.abs(speed) > (climb ? cart.getGravity() + 0.01 : 0.01)) {
					speed += Math.signum(speed) * BOOST;
				} else if (climb) {
					speed = Math.signum(rise) * BOOST;
				}
			} else {
				speed = Math.abs(speed) < HOLD ? 0 : speed * 0.5;
			}
		}
		double max = Rails.maxSpeed(state) / 20.0 * (cart.isInWater() ? 0.5 : 1.0);
		speed = Math.clamp(speed, -max, max);

		NewMinecartBehavior behavior = cart.getBehavior() instanceof NewMinecartBehavior n ? n : null;
		Vec3 start = cart.position();
		double left = Math.abs(speed);
		int direction = speed >= 0 ? 1 : -1;
		Vec3 handOff = null;
		while (left > 1.0E-7) {
			double length = path.length();
			double reach = direction > 0 ? (1 - t) * length : t * length;
			if (left < reach) {
				t += direction * left / length;
				break;
			}
			left -= reach;
			t = direction > 0 ? 1 : 0;
			TrackPath.End end = path.ends().get(direction > 0 ? 1 : 0);
			BlockPos next = MountedRails.joinedAt(level, block, end);
			BlockState nextState = next == null ? null : level.getBlockState(next);
			if (nextState != null && nextState.getBlock() instanceof MountedRailBlock mounted) {
				step(cart, behavior, path, t, start);
				start = cart.position();
				TrackPath nextPath = mounted.path(nextState).offset(next);
				boolean fromStart = nextPath.ends().getFirst().meets(end);
				block = next;
				path = nextPath;
				t = fromStart ? 0 : 1;
				direction = fromStart ? 1 : -1;
				state = nextState;
			} else if (nextState != null && nextState.getBlock() instanceof BaseRailBlock) {
				handOff = path.tangent(t).scale(direction);
				break;
			} else {
				// The end of the line: the cart stops there, as at buffers
				speed = 0;
				left = 0;
			}
		}

		if (handOff != null) {
			// Onto a floor rail, moving along it, a hair inside its block so vanilla finds it there
			Vec3 along = new Vec3(handOff.x, 0, handOff.z).normalize();
			Vec3 at = path.at(t).add(0, 0.1, 0).add(along.scale(1.0E-3));
			cart.noPhysics = true;
			cart.move(MoverType.SELF, at.subtract(cart.position()));
			cart.noPhysics = false;
			cart.setDeltaMovement(along.scale(Math.abs(speed)));
			RIDES.remove(cart.getUUID());
			if (behavior != null) addStep(behavior, cart, start);
			return;
		}

		step(cart, behavior, path, t, start);
		cart.setDeltaMovement(path.tangent(t).scale(direction * Math.abs(speed)));
		face(cart, path.tangent(t).scale(direction));
		cart.resetFallDistance();
		cart.applyEffectsFromBlocks();
		RIDES.put(cart.getUUID(), new Ride(block.immutable(), t));
	}

	/** Put the cart at {@code t} along the path, pushing what it meets, and tell watching clients the way it went. */
	private static void step(AbstractMinecart cart, @Nullable NewMinecartBehavior behavior, TrackPath path, double t, Vec3 from) {
		Vec3 to = middleAt(path, t).subtract(0, HALF_HEIGHT, 0);
		cart.noPhysics = true;
		cart.move(MoverType.SELF, to.subtract(cart.position()));
		cart.noPhysics = false;
		if (behavior != null) addStep(behavior, cart, from);
	}

	private static void addStep(NewMinecartBehavior behavior, AbstractMinecart cart, Vec3 from) {
		float weight = (float) cart.position().distanceTo(from);
		if (weight > 1.0E-5F) {
			behavior.lerpSteps.add(new NewMinecartBehavior.MinecartStep(cart.position(), cart.getDeltaMovement(),
				cart.getYRot(), cart.getXRot(), weight));
		}
	}

	/** Yaw and pitch along the way it is going, for clients that draw a cart only by those two. */
	private static void face(AbstractMinecart cart, Vec3 heading) {
		if (heading.horizontalDistanceSqr() > 1.0E-4) {
			cart.setYRot((float) (180.0 - Math.toDegrees(Math.atan2(heading.z, heading.x))) + (cart.isFlipped() ? 180F : 0F));
		}
		cart.setXRot((float) Math.toDegrees(Math.asin(Math.clamp(heading.y, -1, 1))) * (cart.isFlipped() ? -1F : 1F));
	}
}
