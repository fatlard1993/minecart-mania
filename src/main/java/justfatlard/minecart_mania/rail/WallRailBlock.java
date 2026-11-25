package justfatlard.minecart_mania.rail;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

/**
 * A rail on a wall: along it, up it, turning between the two, or bending onto it from the
 * floor and off it onto the ceiling.
 *
 * <p>{@code facing} is the side of this block the wall is on.
 */
public class WallRailBlock extends MountedRailBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<WallTrack> TRACK = EnumProperty.create("track", WallTrack.class);
	private static final Vec3 CENTRE = new Vec3(0.5, 0.5, 0.5);
	private static final Vec3 UP = new Vec3(0, 1, 0);

	public WallRailBlock(Block floor, Properties properties) {
		super(floor, properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TRACK, WallTrack.ALONG));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TRACK);
	}

	@Override
	public TrackPath path(BlockState state) {
		return path(state.getValue(FACING), state.getValue(TRACK));
	}

	static TrackPath path(Direction facing, WallTrack track) {
		Vec3 wall = Vec3.atLowerCornerOf(facing.getUnitVec3i());
		Vec3 right = Vec3.atLowerCornerOf(facing.getClockWise().getUnitVec3i()).scale(0.5);
		Vec3 up = UP.scale(0.5);
		Vec3 face = CENTRE.add(wall.scale(0.5));
		Vec3 out = wall.reverse();
		Vec3 floorEdge = new Vec3(0.5, 0, 0.5).subtract(wall.scale(0.5));
		Vec3 ceilingEdge = new Vec3(0.5, 1, 0.5).subtract(wall.scale(0.5));
		return switch (track) {
			case ALONG -> TrackPath.line(face.subtract(right), face.add(right), out);
			case CLIMB -> TrackPath.line(face.subtract(up), face.add(up), out);
			case UP_LEFT -> TrackPath.line(face.subtract(right), face.add(up), out);
			case UP_RIGHT -> TrackPath.line(face.add(right), face.add(up), out);
			case DOWN_LEFT -> TrackPath.line(face.subtract(right), face.subtract(up), out);
			case DOWN_RIGHT -> TrackPath.line(face.add(right), face.subtract(up), out);
			case FROM_FLOOR -> TrackPath.arc(floorEdge, face.add(up), ceilingEdge);
			case TO_CEILING -> TrackPath.arc(face.subtract(up), ceilingEdge, floorEdge);
		};
	}

	@Override
	protected Direction[] lyingOn(BlockState state) {
		Direction facing = state.getValue(FACING);
		return switch (state.getValue(TRACK)) {
			case FROM_FLOOR -> new Direction[] {Direction.DOWN, facing};
			case TO_CEILING -> new Direction[] {facing, Direction.UP};
			default -> new Direction[] {facing};
		};
	}

	/** A wall rail with redstone, for the powered rails. */
	public static class Powered extends WallRailBlock {
		public Powered(Block floor, Properties properties) {
			super(floor, properties);
			registerDefaultState(defaultBlockState().setValue(POWERED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, TRACK, POWERED);
		}
	}
}
