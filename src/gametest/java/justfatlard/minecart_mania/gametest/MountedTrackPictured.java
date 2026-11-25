package justfatlard.minecart_mania.gametest;

import justfatlard.minecart_mania.rail.CeilingRailBlock;
import justfatlard.minecart_mania.rail.MountedRails;
import justfatlard.minecart_mania.rail.WallRailBlock;
import justfatlard.minecart_mania.rail.WallTrack;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.gametest.Pictures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Wall and ceiling track as a Pandorical client draws it: a run up from the floor, up the wall
 * and back under a ceiling, and beside it a line along the wall that turns up it and along
 * again. Taken with {@code -Ppictures}; without, it builds the scene and stops.
 */
public final class MountedTrackPictured implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Pictures.wanted()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isContentReady(connection.getServerPlayer()));
			Pictures.stage(context, server, Pictures.MORNING);
			BlockPos base = server.computeOnServer(s -> Pictures.ground(s.overworld(),
				connection.getServerPlayer().getBlockX(), connection.getServerPlayer().getBlockZ()));
			server.runOnServer(s -> build(s.overworld(), base));
			Pictures.look(server, Vec3.atBottomCenterOf(base).add(7, 1.5, 8), Vec3.atBottomCenterOf(base).add(2, 2, 1));
			System.out.println("[minecart-mania test] picture " + Pictures.shoot(context, connection, "mounted-track").toAbsolutePath());
			Pictures.look(server, Vec3.atBottomCenterOf(base).add(-5, 1, 2.5), Vec3.atBottomCenterOf(base).add(0, 2, 1.5));
			System.out.println("[minecart-mania test] picture " + Pictures.shoot(context, connection, "mounted-track-side").toAbsolutePath());
		}
	}

	private static void build(ServerLevel level, BlockPos base) {
		Pictures.clearAround(level, base, 10, 8);
		for (int dx = -1; dx <= 6; dx++) {
			for (int dz = -1; dz <= 5; dz++) level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
			for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(base.offset(dx, dy, -1), Blocks.STONE.defaultBlockState());
		}
		for (int dz = 0; dz <= 4; dz++) level.setBlockAndUpdate(base.offset(0, 4, dz), Blocks.STONE.defaultBlockState());

		WallRailBlock wall = MountedRails.kindOf(Blocks.RAIL).wall();
		CeilingRailBlock ceiling = MountedRails.kindOf(Blocks.RAIL).ceiling();
		for (int dz = 1; dz <= 3; dz++) level.setBlock(base.offset(0, 0, dz), Blocks.RAIL.defaultBlockState(), 2);
		level.setBlock(base, wall(wall, WallTrack.FROM_FLOOR), 2);
		level.setBlock(base.above(), wall(wall, WallTrack.CLIMB), 2);
		level.setBlock(base.above(2), wall(wall, WallTrack.CLIMB), 2);
		level.setBlock(base.above(3), wall(wall, WallTrack.TO_CEILING), 2);
		for (int dz = 1; dz <= 3; dz++) {
			level.setBlock(base.offset(0, 3, dz), ceiling.defaultBlockState().setValue(CeilingRailBlock.TRACK, RailShape.NORTH_SOUTH), 2);
		}
		level.setBlock(base.offset(0, 3, 4), ceiling.defaultBlockState().setValue(CeilingRailBlock.TRACK, RailShape.NORTH_EAST), 2);

		level.setBlock(base.offset(2, 1, 0), wall(wall, WallTrack.ALONG), 2);
		level.setBlock(base.offset(3, 1, 0), wall(wall, WallTrack.UP_LEFT), 2);
		level.setBlock(base.offset(3, 2, 0), wall(wall, WallTrack.CLIMB), 2);
		level.setBlock(base.offset(3, 3, 0), wall(wall, WallTrack.DOWN_RIGHT), 2);
		level.setBlock(base.offset(4, 3, 0), wall(wall, WallTrack.ALONG), 2);
		level.setBlock(base.offset(5, 3, 0), wall(wall, WallTrack.ALONG), 2);
	}

	private static BlockState wall(WallRailBlock block, WallTrack track) {
		return block.defaultBlockState().setValue(WallRailBlock.FACING, Direction.NORTH).setValue(WallRailBlock.TRACK, track);
	}
}
