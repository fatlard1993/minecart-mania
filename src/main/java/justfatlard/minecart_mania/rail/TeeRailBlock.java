package justfatlard.minecart_mania.rail;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * A junction: a main line with a branch that merges onto it.
 *
 * <p>{@code stem} is the arm the branch comes in on; the main line is the axis across it.
 * A cart arriving along the main line goes straight. A cart arriving down the stem curves
 * onto the main line, to the left of its travel or the right, which is what {@code left}
 * says and what a shift-click on the junction flips. The main line never turns up the stem:
 * a merge is one way, which is what makes it a junction rather than a switch.
 */
public class TeeRailBlock extends SwitchingRailBlock {
	public static final EnumProperty<RailShape> SHAPE = EnumProperty.create("shape", RailShape.class,
		RailShape.NORTH_SOUTH, RailShape.EAST_WEST,
		RailShape.NORTH_EAST, RailShape.NORTH_WEST, RailShape.SOUTH_EAST, RailShape.SOUTH_WEST);
	public static final EnumProperty<Direction> STEM = EnumProperty.create("stem", Direction.class, Direction.Plane.HORIZONTAL);
	public static final BooleanProperty LEFT = BooleanProperty.create("left");
	/**
	 * Whether a signal is on the block, so that a change can be told from a repeat. Power flips
	 * the branch the way it flips a vanilla curve at a fork: on swings it one way, off swings it
	 * back, and a shift-click still turns it by hand in between.
	 */
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public TeeRailBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
			.setValue(SHAPE, RailShape.EAST_WEST)
			.setValue(STEM, Direction.NORTH)
			.setValue(LEFT, false)
			.setValue(POWERED, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	public Property<RailShape> getShapeProperty() {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, STEM, LEFT, POWERED, WATERLOGGED);
	}

	/** The stem points back at the player: you stand on the branch and lay the junction ahead. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction stem = context.getHorizontalDirection().getOpposite();
		return super.getStateForPlacement(context)
			.setValue(STEM, stem)
			.setValue(SHAPE, straight(stem.getClockWise().getAxis()));
	}

	/** Where the stem hands a cart off to: to the left of travel down the stem, or the right. */
	public Direction mergeDirection(BlockState state) {
		Direction travel = state.getValue(STEM).getOpposite();
		return state.getValue(LEFT) ? travel.getCounterClockWise() : travel.getClockWise();
	}

	@Override
	public RailShape shapeFor(BlockState state, Vec3 motion) {
		Direction heading = heading(motion);
		if (heading == null) return null;
		Direction stem = state.getValue(STEM);
		if (heading.getAxis() == stem.getAxis()) {
			// Down the stem into the junction: curve onto the main line. Up the stem is a cart
			// that just merged and is being read a step late; the curve still fits it.
			return curve(stem, mergeDirection(state));
		}
		return straight(heading.getAxis());
	}

	public BlockState flipped(BlockState state) {
		return state.setValue(LEFT, !state.getValue(LEFT));
	}

	/** A signal arriving or leaving swings the branch, the way it swings a fork's curve in vanilla. */
	@Override
	protected void updateState(BlockState state, Level level, BlockPos pos, Block neighbour) {
		boolean powered = level.hasNeighborSignal(pos);
		if (powered == state.getValue(POWERED)) return;
		level.setBlock(pos, flipped(state).setValue(POWERED, powered), Block.UPDATE_ALL);
	}
}
