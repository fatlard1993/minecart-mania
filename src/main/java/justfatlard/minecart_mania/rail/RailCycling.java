package justfatlard.minecart_mania.rail;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shift-click a rail with an empty hand to step it through the shapes it could take.
 *
 * <p>Only shapes whose two ends actually meet a rail are offered, so the cycle is the set of
 * ways this piece could really join its neighbours, and a rail with one way to sit is left
 * alone. The shape chosen is pinned, so the next block placed nearby cannot undo it; a
 * junction flips which way its branch merges instead. A powered rail is redstone's to shape.
 */
public final class RailCycling {
	private RailCycling() {}

	public static void register() {
		UseBlockCallback.EVENT.register(RailCycling::onUse);
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BaseRailBlock rail)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		if (level.hasNeighborSignal(pos)) {
			player.sendOverlayMessage(Component.literal("Redstone holds this rail"));
			return InteractionResult.SUCCESS;
		}

		if (rail instanceof TeeRailBlock tee) {
			BlockState flipped = tee.flipped(state);
			level.setBlock(pos, flipped, Block.UPDATE_ALL);
			player.sendOverlayMessage(Component.literal(
				"Branch merges " + tee.mergeDirection(flipped).getName()));
			return InteractionResult.SUCCESS;
		}
		if (rail instanceof CrossRailBlock) return InteractionResult.PASS;

		Property<RailShape> property = rail.getShapeProperty();
		List<RailShape> options = candidates(level, pos, property);
		if (options.size() <= 1) {
			player.sendOverlayMessage(Component.literal("Only one way this rail can sit"));
			return InteractionResult.SUCCESS;
		}
		RailShape current = state.getValue(property);
		RailShape next = options.get((options.indexOf(current) + 1) % options.size());
		level.setBlock(pos, state.setValue(property, next), Block.UPDATE_ALL);
		RailPins.pin(server, pos);
		player.sendOverlayMessage(Component.literal("Rail set " + next.getSerializedName()
			+ " (" + (options.indexOf(next) + 1) + " of " + options.size() + ")"));
		return InteractionResult.SUCCESS;
	}

	/** The shapes whose both ends find a rail, at the exit or one block down from it. */
	static List<RailShape> candidates(Level level, BlockPos pos, Property<RailShape> property) {
		List<RailShape> options = new ArrayList<>();
		for (RailShape shape : property.getPossibleValues()) {
			Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
			if (meetsRail(level, pos, exits.getFirst()) && meetsRail(level, pos, exits.getSecond())) {
				options.add(shape);
			}
		}
		return options;
	}

	private static boolean meetsRail(Level level, BlockPos pos, Vec3i exit) {
		BlockPos there = pos.offset(exit);
		return Rails.isRail(level.getBlockState(there)) || Rails.isRail(level.getBlockState(there.below()));
	}
}
