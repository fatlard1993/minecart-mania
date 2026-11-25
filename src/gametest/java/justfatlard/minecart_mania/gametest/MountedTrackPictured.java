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
			context.waitTicks(40);
			Pictures.look(server, Vec3.atBottomCenterOf(base).add(7, 1.5, 8), Vec3.atBottomCenterOf(base).add(2, 2, 1));
			System.out.println("[minecart-mania test] picture " + Pictures.shoot(context, connection, "mounted-track").toAbsolutePath());
			Pictures.look(server, Vec3.atBottomCenterOf(base).add(-5, 1, 2.5), Vec3.atBottomCenterOf(base).add(0, 2, 1.5));
			System.out.println("[minecart-mania test] picture " + Pictures.shoot(context, connection, "mounted-track-side").toAbsolutePath());
			Pictures.look(server, Vec3.atBottomCenterOf(base).add(-3.5, 1.2, 2.5), Vec3.atBottomCenterOf(base).add(0, 2.4, 2.5));
			System.out.println("[minecart-mania test] picture " + Pictures.shoot(context, connection, "mounted-track-hanging").toAbsolutePath());
		}
	}

	private static void build(ServerLevel level, BlockPos base) {
		Pictures.clearAround(level, base, 10, 8);
		for (int dx = -1; dx <= 6; dx++) {
			for (int dz = -1; dz <= 5; dz++) level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
			for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(base.offset(dx, dy, -1), Blocks.STONE.defaultBlockState());
		}
		for (int dz = 0; dz <= 4; dz++) level.setBlockAndUpdate(base.offset(0, 4, dz), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(base.offset(-2, 0, 2), Blocks.SEA_LANTERN.defaultBlockState());

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

		// A cart hanging under the ceiling with a rider, and one standing on the wall
		var hanging = cart(level, new Vec3(base.getX() + 0.5, base.getY() + 3.2, base.getZ() + 2.5));
		var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.snapTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 2.5, 0F, 0F);
		level.addFreshEntity(zombie);
		zombie.startRiding(hanging, true, true);
		cart(level, new Vec3(base.getX() + 4.5, base.getY() + 3.15, base.getZ() + 0.45));
	}

	private static net.minecraft.world.entity.vehicle.minecart.AbstractMinecart cart(ServerLevel level, Vec3 at) {
		var cart = net.minecraft.world.entity.EntityTypes.MINECART.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		cart.snapTo(at.x, at.y, at.z, 0F, 0F);
		level.addFreshEntity(cart);
		return cart;
	}

	private static BlockState wall(WallRailBlock block, WallTrack track) {
		return block.defaultBlockState().setValue(WallRailBlock.FACING, Direction.NORTH).setValue(WallRailBlock.TRACK, track);
	}
}
