package justfatlard.minecart_mania.rail;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A rail laid on a wall or a ceiling rather than a floor.
 *
 * <p>Not a {@code BaseRailBlock} and not in the {@code minecraft:rails} tag, on purpose:
 * vanilla's cart takes anything in that tag for a floor rail and casts it to one, and its
 * movement only knows how to roll along a floor. Carts on these are driven by
 * {@code SurfaceRide} instead.
 *
 * <p>Each mounted block stands for one floor rail, which it drops and picks as, and whose speed
 * it runs at. A block whose floor rail takes power has {@link #POWERED}, reaching eight rails
 * along its own line the way a powered rail's signal does.
 */
public abstract class MountedRailBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private static final int POWER_REACH = 8;

	private final Block floor;

	protected MountedRailBlock(Block floor, Properties properties) {
		super(properties);
		this.floor = floor;
	}

	/** The floor rail this one is laid from. */
	public Block floor() {
		return floor;
	}

	/** The path through this block, in its own coordinates, nought to one on each axis. */
	public abstract TrackPath path(BlockState state);

	/** The faces of the block the rail lies against, for the outline a player aims at. */
	protected abstract Direction[] lyingOn(BlockState state);

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = null;
		for (Direction side : lyingOn(state)) {
			VoxelShape plate = plate(side);
			shape = shape == null ? plate : net.minecraft.world.phys.shapes.Shapes.or(shape, plate);
		}
		return shape;
	}

	private static VoxelShape plate(Direction side) {
		return switch (side) {
			case DOWN -> Block.box(0, 0, 0, 16, 2, 16);
			case UP -> Block.box(0, 14, 0, 16, 16, 16);
			case NORTH -> Block.box(0, 0, 0, 16, 16, 2);
			case SOUTH -> Block.box(0, 0, 14, 16, 16, 16);
			case WEST -> Block.box(0, 0, 0, 2, 16, 16);
			case EAST -> Block.box(14, 0, 0, 16, 16, 16);
		};
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(floor);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(state.getBlock())) updatePower(state, level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		updatePower(state, level, pos);
	}

	private void updatePower(BlockState state, Level level, BlockPos pos) {
		if (level.isClientSide() || !state.hasProperty(POWERED)) return;
		boolean powered = signalAlongLine(level, pos);
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
		}
	}

	/** Whether redstone reaches this rail: on it, or on a joined rail of its own kind within reach. */
	private boolean signalAlongLine(Level level, BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		frontier.add(start);
		seen.add(start);
		for (int depth = 0; depth <= POWER_REACH && !frontier.isEmpty(); depth++) {
			ArrayDeque<BlockPos> next = new ArrayDeque<>();
			for (BlockPos pos : frontier) {
				if (level.hasNeighborSignal(pos)) return true;
				for (BlockPos joined : MountedRails.joinedNeighbours(level, pos)) {
					if (level.getBlockState(joined).is(this) && seen.add(joined)) next.add(joined);
				}
			}
			frontier = next;
		}
		return false;
	}
}
