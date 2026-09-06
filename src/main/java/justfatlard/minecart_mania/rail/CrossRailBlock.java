package justfatlard.minecart_mania.rail;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Two lines crossing at grade. A cart on either goes straight through.
 *
 * <p>The shape is whichever axis the last cart arrived on; both are drawn, so the state is
 * invisible and only the physics cares.
 */
public class CrossRailBlock extends SwitchingRailBlock {
	public static final EnumProperty<RailShape> SHAPE =
		EnumProperty.create("shape", RailShape.class, RailShape.NORTH_SOUTH, RailShape.EAST_WEST);

	public CrossRailBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SHAPE, RailShape.NORTH_SOUTH).setValue(WATERLOGGED, false));
	}

	@Override
	public Property<RailShape> getShapeProperty() {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState placed = super.getStateForPlacement(context);
		return placed.setValue(SHAPE, straight(context.getHorizontalDirection().getAxis()));
	}

	@Override
	public RailShape shapeFor(BlockState state, Vec3 motion) {
		Direction heading = heading(motion);
		return heading == null ? null : straight(heading.getAxis());
	}
}
