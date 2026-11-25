package justfatlard.minecart_mania.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import justfatlard.minecart_mania.rail.CeilingRailBlock;
import justfatlard.minecart_mania.rail.MountedRails;
import justfatlard.minecart_mania.rail.WallRailBlock;
import justfatlard.minecart_mania.rail.WallTrack;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Track laid up a wall and over a ceiling the way a player lays it: a floor run to the wall,
 * rails held against the wall looking up, a rail held against the ceiling. The floor rail at
 * the foot turns into the bend up the wall, the top of the climb into the bend over onto the
 * ceiling, and every block of it joins the next.
 */
public final class MountedTrackLays implements FabricClientGameTest {
	private static final String MOD_ID = "minecart-mania-justfatlard";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, MOD_ID, session -> {
			AtomicReference<String> failure = new AtomicReference<>();
			session.onServer(server -> {
				try {
					lay(server.overworld(), session.player());
				} catch (AssertionError e) {
					failure.set(e.getMessage());
				}
			});
			if (failure.get() != null) throw new AssertionError(failure.get());
		});
		System.out.println("[minecart-mania test] PASSED MountedTrackLays");
	}

	private static void lay(ServerLevel level, ServerPlayer player) {
		// The track runs north to a wall, up it, and back south under a ceiling.
		BlockPos foot = player.blockPosition().offset(6, 12, 0);
		for (int dy = -1; dy <= 5; dy++) {
			for (int dz = -1; dz <= 3; dz++) level.setBlockAndUpdate(foot.offset(0, dy, dz), Blocks.AIR.defaultBlockState());
		}
		for (int dz = 0; dz <= 3; dz++) level.setBlockAndUpdate(foot.offset(0, -1, dz), Blocks.STONE.defaultBlockState());
		for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(foot.offset(0, dy, -1), Blocks.STONE.defaultBlockState());
		for (int dz = 0; dz <= 3; dz++) level.setBlockAndUpdate(foot.offset(0, 4, dz), Blocks.STONE.defaultBlockState());

		level.setBlockAndUpdate(foot.south(), Blocks.RAIL.defaultBlockState());
		level.setBlockAndUpdate(foot, Blocks.RAIL.defaultBlockState());
		check(level.getBlockState(foot).is(Blocks.RAIL), "a floor rail with no climb above it changed");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RAIL, 64));
		player.setXRot(-60);
		for (int dy = 1; dy <= 3; dy++) use(level, player, foot.offset(0, dy, -1), Direction.SOUTH);
		check(wall(level, foot, WallTrack.FROM_FLOOR), "the floor rail under the climb is not the bend up the wall: " + level.getBlockState(foot));
		check(wall(level, foot.above(), WallTrack.CLIMB), "a rail held against the wall looking up does not climb: " + level.getBlockState(foot.above()));
		check(wall(level, foot.above(3), WallTrack.CLIMB), "the top of the climb is not a climb: " + level.getBlockState(foot.above(3)));

		player.setXRot(0);
		player.setYRot(180);
		use(level, player, foot.offset(0, 4, 1), Direction.DOWN);
		BlockState ceiling = level.getBlockState(foot.offset(0, 3, 1));
		check(ceiling.getBlock() instanceof CeilingRailBlock && ceiling.getValue(CeilingRailBlock.TRACK) == RailShape.NORTH_SOUTH,
			"a rail held against the ceiling is not a ceiling rail running north: " + ceiling);
		check(wall(level, foot.above(3), WallTrack.TO_CEILING), "the top of the climb did not bend onto the ceiling: " + level.getBlockState(foot.above(3)));

		// Every block from the floor to the ceiling joins the one before and the one after.
		List<BlockPos> line = List.of(foot.south(), foot, foot.above(), foot.above(2), foot.above(3), foot.offset(0, 3, 1));
		for (int i = 0; i < line.size(); i++) {
			List<BlockPos> joined = MountedRails.joinedNeighbours(level, line.get(i));
			if (i > 0) check(joined.contains(line.get(i - 1)), line.get(i) + " does not join " + line.get(i - 1) + ": " + joined);
			if (i < line.size() - 1) check(joined.contains(line.get(i + 1)), line.get(i) + " does not join " + line.get(i + 1) + ": " + joined);
		}
		// The ceiling rail does not join the floor of the block above it, though their ends meet.
		check(!MountedRails.joinedNeighbours(level, foot.offset(0, 3, 1)).contains(foot.offset(0, 4, 1)), "a ceiling rail joined the block above it");

		// Held against another rail, a rail is a floor rail, as before.
		player.setXRot(0);
		use(level, player, foot.south(), Direction.SOUTH);
		check(level.getBlockState(foot.south(2)).is(Blocks.RAIL), "a rail held against a rail was not laid on the floor: " + level.getBlockState(foot.south(2)));
	}

	private static void use(ServerLevel level, ServerPlayer player, BlockPos against, Direction face) {
		Vec3 hit = Vec3.atCenterOf(against).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
		var result = UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, against, false));
		if (!result.consumesAction()) {
			// What vanilla does when nothing claims the click
			player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(hit, face, against, false)));
		}
	}

	private static boolean wall(ServerLevel level, BlockPos pos, WallTrack track) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof WallRailBlock && state.getValue(WallRailBlock.FACING) == Direction.NORTH
			&& state.getValue(WallRailBlock.TRACK) == track;
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
