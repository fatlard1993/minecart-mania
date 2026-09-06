package justfatlard.minecart_mania.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * A rail that sets its own shape to suit the cart arriving on it, and never lets its
 * neighbours set it.
 *
 * <p>A cart reads one {@link RailShape} off the block under it each step, so a crossing or a
 * junction cannot be a shape: it has to become the right one just before the cart needs it.
 * {@link #shapeFor} answers that from the cart's heading, and the movement hook applies it a
 * step ahead. Vanilla's neighbour logic is switched off here, because a block that reconnected
 * itself to whatever was placed beside it would stop being a crossing the moment a line ran
 * past it.
 */
public abstract class SwitchingRailBlock extends BaseRailBlock {
	protected SwitchingRailBlock(Properties properties) {
		super(false, properties);
	}

	/** The shape this block should hold for a cart moving {@code motion}, or null to leave it. */
	public abstract RailShape shapeFor(BlockState state, Vec3 motion);

	/** Neighbours do not shape this rail. */
	@Override
	protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean alwaysPlace) {
		return state;
	}

	/** The compass direction a horizontal motion mostly points, or null when it is not moving. */
	protected static Direction heading(Vec3 motion) {
		if (motion.horizontalDistanceSqr() < 1.0E-6) return null;
		return Math.abs(motion.x) >= Math.abs(motion.z)
			? (motion.x > 0 ? Direction.EAST : Direction.WEST)
			: (motion.z > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	protected static RailShape straight(Direction.Axis axis) {
		return axis == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
	}

	/** The curve joining two perpendicular arms, named the way vanilla names it. */
	protected static RailShape curve(Direction a, Direction b) {
		boolean north = a == Direction.NORTH || b == Direction.NORTH;
		boolean east = a == Direction.EAST || b == Direction.EAST;
		if (north) return east ? RailShape.NORTH_EAST : RailShape.NORTH_WEST;
		return east ? RailShape.SOUTH_EAST : RailShape.SOUTH_WEST;
	}
}
