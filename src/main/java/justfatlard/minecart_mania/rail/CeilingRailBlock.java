package justfatlard.minecart_mania.rail;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * A rail on the underside of a ceiling, ridden upside down: every shape a floor rail takes,
 * hung from the top of the block.
 *
 * <p>The shape is called {@code track}, not {@code shape}, so that nothing which picks out
 * floor rails by a {@code shape} property holding a {@link RailShape} (Pandorical's standing
 * deck and diagonal chords) mistakes it for one.
 */
public class CeilingRailBlock extends MountedRailBlock {
	public static final EnumProperty<RailShape> TRACK = EnumProperty.create("track", RailShape.class);
	private static final Vec3 DOWN = new Vec3(0, -1, 0);
	private static final Vec3 TOP = new Vec3(0.5, 1, 0.5);
	private static final Vec3 BOTTOM = new Vec3(0.5, 0, 0.5);

	public CeilingRailBlock(Block floor, Properties properties) {
		super(floor, properties);
		registerDefaultState(stateDefinition.any().setValue(TRACK, RailShape.NORTH_SOUTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TRACK);
	}

	@Override
	public TrackPath path(BlockState state) {
		return path(state.getValue(TRACK));
	}

	/**
	 * A slope is the same line through the block a floor slope is, from the low face's bottom
	 * edge to the high face's top edge, with the cart on its underside.
	 */
	static TrackPath path(RailShape shape) {
		Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
		Vec3 a = Vec3.atLowerCornerOf(exits.getFirst());
		Vec3 b = Vec3.atLowerCornerOf(exits.getSecond());
		if (shape.isSlope()) {
			Vec3 rise = a.y > 0 ? a.horizontal() : b.horizontal();
			return TrackPath.line(BOTTOM.subtract(rise.scale(0.5)), TOP.add(rise.scale(0.5)), DOWN.add(rise).normalize());
		}
		return TrackPath.line(TOP.add(a.scale(0.5)), TOP.add(b.scale(0.5)), DOWN);
	}

	@Override
	protected Direction[] lyingOn(BlockState state) {
		return new Direction[] {Direction.UP};
	}

	/** A ceiling rail with redstone, for the powered rails. */
	public static class Powered extends CeilingRailBlock {
		public Powered(Block floor, Properties properties) {
			super(floor, properties);
			registerDefaultState(defaultBlockState().setValue(POWERED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(TRACK, POWERED);
		}
	}
}
