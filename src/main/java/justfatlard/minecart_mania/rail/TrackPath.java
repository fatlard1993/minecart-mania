package justfatlard.minecart_mania.rail;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The line a cart follows through one block of track, from one end to the other, with the side
 * the cart rides on at every point.
 *
 * <p>A path is straight, or a quarter circle about {@code centre}: the bends from floor to wall
 * and wall to ceiling are the only arcs, and on an arc the cart's side is always toward the
 * centre. Curves within one face are straight chords, as vanilla moves a cart round a floor
 * curve.
 */
public record TrackPath(Vec3 from, Vec3 to, Vec3 fromUp, Vec3 toUp, @Nullable Vec3 centre) {

	/** One end of a path: where it meets the next block, and which side of it the cart is on. */
	public record End(Vec3 point, Vec3 up) {
		private static final double SAME = 1.0E-6;

		/** Two ends join where they are the same point with the cart on the same side of both. */
		public boolean meets(End other) {
			return point.distanceToSqr(other.point) < SAME && up.dot(other.up) > 0.5;
		}

		public End offset(BlockPos pos) {
			return new End(point.add(pos.getX(), pos.getY(), pos.getZ()), up);
		}
	}

	public static TrackPath line(Vec3 from, Vec3 to, Vec3 up) {
		return new TrackPath(from, to, up, up, null);
	}

	public static TrackPath arc(Vec3 from, Vec3 to, Vec3 centre) {
		return new TrackPath(from, to, centre.subtract(from).normalize(), centre.subtract(to).normalize(), centre);
	}

	public List<End> ends() {
		return List.of(new End(from, fromUp), new End(to, toUp));
	}

	public TrackPath offset(BlockPos pos) {
		Vec3 by = Vec3.atLowerCornerOf(pos);
		return new TrackPath(from.add(by), to.add(by), fromUp, toUp, centre == null ? null : centre.add(by));
	}

	public double length() {
		return centre == null ? from.distanceTo(to) : from.distanceTo(centre) * Math.PI / 2;
	}

	/** The point a fraction {@code t} of the way from {@code from} to {@code to}. */
	public Vec3 at(double t) {
		if (centre == null) return from.lerp(to, t);
		double angle = t * Math.PI / 2;
		return centre.add(from.subtract(centre).scale(Math.cos(angle))).add(to.subtract(centre).scale(Math.sin(angle)));
	}

	/** Which way the cart's top faces at {@code t}. */
	public Vec3 up(double t) {
		if (centre == null) return fromUp.lerp(toUp, t).normalize();
		return centre.subtract(at(t)).normalize();
	}

	/** The direction of travel from {@code from} toward {@code to}, at {@code t}. */
	public Vec3 tangent(double t) {
		if (centre == null) return to.subtract(from).normalize();
		double angle = t * Math.PI / 2;
		return from.subtract(centre).scale(-Math.sin(angle)).add(to.subtract(centre).scale(Math.cos(angle))).normalize();
	}
}
