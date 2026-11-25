package justfatlard.minecart_mania.gametest;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import justfatlard.minecart_mania.rail.CeilingRailBlock;
import justfatlard.minecart_mania.rail.MountedRails;
import justfatlard.minecart_mania.rail.WallRailBlock;
import justfatlard.minecart_mania.rail.WallTrack;
import justfatlard.pandorical.gametest.Smoke;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Carts ridden on wall and ceiling track: round a loop from the floor up a wall, along the
 * ceiling upside down, down the far wall and back onto the floor; back down a climb taken too
 * slowly; held under a ceiling at a standstill; and lifted up a powered climb from rest.
 */
public final class MountedTrackRides implements FabricClientGameTest {
	private static final String MOD_ID = "minecart-mania-justfatlard";

	@Override
	public void runTest(ClientGameTestContext context) {
		Smoke.run(context, MOD_ID, session -> {
			BlockPos origin = ask(session, server -> session.player().blockPosition().offset(6, 14, 0));
			int[] carts = ask(session, server -> {
				ServerLevel level = server.overworld();
				return new int[] {
					loop(level, origin),
					stall(level, origin.offset(6, 0, 0)),
					hang(level, origin.offset(12, 0, 0)),
					lift(level, origin.offset(18, 0, 0)),
					setDown(level, session.player(), origin.offset(24, 0, 0)),
				};
			});

			double loopTop = Double.NEGATIVE_INFINITY;
			boolean loopUnderCeiling = false;
			boolean loopBackOnFloor = false;
			double stallTop = Double.NEGATIVE_INFINITY;
			for (int tick = 0; tick < 120; tick++) {
				session.waitTicks(1);
				Vec3[] at = ask(session, server -> positions(server.overworld(), carts));
				loopTop = Math.max(loopTop, at[0].y);
				if (at[0].y > origin.getY() + 2.5 && at[0].z > origin.getZ() + 1 && at[0].z < origin.getZ() + 4) loopUnderCeiling = true;
				if (loopUnderCeiling && at[0].y < origin.getY() + 0.5) loopBackOnFloor = true;
				stallTop = Math.max(stallTop, at[1].y);
			}

			Vec3[] at = ask(session, server -> positions(server.overworld(), carts));
			check(loopUnderCeiling, "the loop's cart never ran under the ceiling; highest " + (loopTop - origin.getY()));
			check(loopBackOnFloor, "the loop's cart never came back down onto the floor");
			BlockPos stall = origin.offset(6, 0, 0);
			check(stallTop < stall.getY() + 5, "a slow cart climbed to the top of a climb it had no speed for: " + (stallTop - stall.getY()));
			check(stallTop > stall.getY() + 1.5, "a cart rolling onto a climb did not get up it: " + (stallTop - stall.getY()));
			check(at[1].y < stall.getY() + 0.5 && at[1].z > stall.getZ() + 0.5, "a stalled cart did not roll back onto the floor: " + at[1]);
			BlockPos hang = origin.offset(12, 0, 0);
			check(Math.abs(at[2].y - (hang.getY() + 0.2)) < 0.05, "a cart under a ceiling did not stay hung there: " + (at[2].y - hang.getY()));
			String seat = ask(session, server -> {
				var cart = server.overworld().getEntity(carts[2]);
				var rider = cart.getFirstPassenger();
				if (rider == null) return "the hanging cart lost its rider";
				if (rider.getBoundingBox().maxY > hang.getY() + 1 + 1.0E-3) return "a rider under a ceiling has their head in it: top " + (rider.getBoundingBox().maxY - hang.getY());
				if (rider.getBoundingBox().minY >= cart.getBoundingBox().minY) return "a rider under a ceiling is not hanging below the cart: bottom " + (rider.getBoundingBox().minY - hang.getY());
				return "";
			});
			check(seat.isEmpty(), seat);
			// The client is told how the cart is turned, and seats the rider where the server does
			String onClient = session.context().computeOnClient(client -> {
				var cart = client.level.getEntity(carts[2]);
				if (cart == null) return "the client has no hanging cart";
				if (justfatlard.pandorical.push.EntityOrientations.of(cart) == null) return "the client was not told the hanging cart is turned";
				var rider = cart.getFirstPassenger();
				if (rider == null) return "the client's hanging cart has no rider";
				if (rider.getBoundingBox().maxY > hang.getY() + 1 + 1.0E-3) return "the client seats a rider under a ceiling with their head in it";
				return "";
			});
			check(onClient.isEmpty(), onClient);
			BlockPos lift = origin.offset(18, 0, 0);
			check(at[3].y > lift.getY() + 3, "a powered climb did not lift a cart from rest: " + (at[3].y - lift.getY()));
			BlockPos setDown = origin.offset(24, 0, 0);
			check(Math.abs(at[4].y - (setDown.getY() + 0.2)) < 0.05, "a minecart set down on a ceiling rail did not hang there: " + (at[4].y - setDown.getY()));
		});
		System.out.println("[minecart-mania test] PASSED MountedTrackRides");
	}

	/**
	 * A box loop on its end, every block of it powered: a floor of powered rails on redstone
	 * north to a redstone wall, up it, back along a redstone ceiling, down the south wall and
	 * onto the floor again.
	 */
	private static int loop(ServerLevel level, BlockPos o) {
		clear(level, o, 6, 6);
		WallRailBlock wall = MountedRails.kindOf(Blocks.POWERED_RAIL).wall();
		CeilingRailBlock ceiling = MountedRails.kindOf(Blocks.POWERED_RAIL).ceiling();
		for (int dz = -1; dz <= 5; dz++) {
			set(level, o.offset(0, -1, dz), Blocks.REDSTONE_BLOCK.defaultBlockState());
			set(level, o.offset(0, 4, dz), Blocks.REDSTONE_BLOCK.defaultBlockState());
		}
		for (int dy = 0; dy <= 3; dy++) {
			set(level, o.offset(0, dy, -1), Blocks.REDSTONE_BLOCK.defaultBlockState());
			set(level, o.offset(0, dy, 5), Blocks.REDSTONE_BLOCK.defaultBlockState());
		}
		for (int dz = 1; dz <= 3; dz++) {
			set(level, o.offset(0, 0, dz), Blocks.POWERED_RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.PoweredRailBlock.POWERED, true));
			set(level, o.offset(0, 3, dz), ceiling.defaultBlockState().setValue(CeilingRailBlock.TRACK, RailShape.NORTH_SOUTH));
		}
		set(level, o, wall(wall, Direction.NORTH, WallTrack.FROM_FLOOR));
		set(level, o.above(), wall(wall, Direction.NORTH, WallTrack.CLIMB));
		set(level, o.above(2), wall(wall, Direction.NORTH, WallTrack.CLIMB));
		set(level, o.above(3), wall(wall, Direction.NORTH, WallTrack.TO_CEILING));
		set(level, o.offset(0, 0, 4), wall(wall, Direction.SOUTH, WallTrack.FROM_FLOOR));
		set(level, o.offset(0, 1, 4), wall(wall, Direction.SOUTH, WallTrack.CLIMB));
		set(level, o.offset(0, 2, 4), wall(wall, Direction.SOUTH, WallTrack.CLIMB));
		set(level, o.offset(0, 3, 4), wall(wall, Direction.SOUTH, WallTrack.TO_CEILING));
		updatePower(level, o, 6);
		return cart(level, Vec3.atBottomCenterOf(o.offset(0, 0, 2)), new Vec3(0, 0, -0.5));
	}

	/** Iron track to a wall and six blocks up it, with a dead end at the top, and a cart rolling at it gently. */
	private static int stall(ServerLevel level, BlockPos o) {
		clear(level, o, 8, 6);
		WallRailBlock wall = MountedRails.kindOf(Blocks.RAIL).wall();
		for (int dz = 0; dz <= 5; dz++) set(level, o.offset(0, -1, dz), Blocks.STONE.defaultBlockState());
		for (int dy = 0; dy <= 6; dy++) set(level, o.offset(0, dy, -1), Blocks.STONE.defaultBlockState());
		for (int dz = 1; dz <= 5; dz++) set(level, o.offset(0, 0, dz), Blocks.RAIL.defaultBlockState());
		set(level, o, wall(wall, Direction.NORTH, WallTrack.FROM_FLOOR));
		for (int dy = 1; dy <= 6; dy++) set(level, o.above(dy), wall(wall, Direction.NORTH, WallTrack.CLIMB));
		return cart(level, Vec3.atBottomCenterOf(o.offset(0, 0, 2)), new Vec3(0, 0, -0.5));
	}

	/** A cart set down on a ceiling rail, not moving. */
	private static int hang(ServerLevel level, BlockPos o) {
		clear(level, o, 4, 4);
		CeilingRailBlock ceiling = MountedRails.kindOf(Blocks.RAIL).ceiling();
		for (int dz = 0; dz <= 2; dz++) {
			set(level, o.offset(0, 1, dz), Blocks.STONE.defaultBlockState());
			set(level, o.offset(0, 0, dz), ceiling.defaultBlockState().setValue(CeilingRailBlock.TRACK, RailShape.NORTH_SOUTH));
		}
		int id = cart(level, new Vec3(o.getX() + 0.5, o.getY() + 0.2, o.getZ() + 1.5), Vec3.ZERO);
		var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
		if (pig == null) throw new AssertionError("could not make a pig");
		pig.setNoAi(true);
		pig.snapTo(o.getX() + 0.5, o.getY() - 1, o.getZ() + 1.5, 0F, 0F);
		level.addFreshEntity(pig);
		check(pig.startRiding(level.getEntity(id), true, true), "the pig would not get in the cart");
		return id;
	}

	/** A minecart item used on a ceiling rail, the way a player sets one down. */
	private static int setDown(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos o) {
		clear(level, o, 4, 4);
		CeilingRailBlock ceiling = MountedRails.kindOf(Blocks.RAIL).ceiling();
		for (int dz = 0; dz <= 2; dz++) {
			set(level, o.offset(0, 1, dz), Blocks.STONE.defaultBlockState());
			set(level, o.offset(0, 0, dz), ceiling.defaultBlockState().setValue(CeilingRailBlock.TRACK, RailShape.NORTH_SOUTH));
		}
		BlockPos rail = o.offset(0, 0, 1);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MINECART));
		var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(rail), Direction.DOWN, rail, false);
		var used = player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		check(used.consumesAction(), "a minecart would not go on a ceiling rail: " + used);
		var carts = level.getEntitiesOfClass(AbstractMinecart.class, new net.minecraft.world.phys.AABB(rail).inflate(0.5));
		check(carts.size() == 1, "setting a minecart on a ceiling rail made " + carts.size() + " carts");
		return carts.getFirst().getId();
	}

	/** A powered climb up a redstone wall, with a cart at rest at its foot. */
	private static int lift(ServerLevel level, BlockPos o) {
		clear(level, o, 8, 3);
		WallRailBlock wall = MountedRails.kindOf(Blocks.POWERED_RAIL).wall();
		for (int dy = 0; dy <= 6; dy++) {
			set(level, o.offset(0, dy, -1), Blocks.REDSTONE_BLOCK.defaultBlockState());
			set(level, o.above(dy), wall(wall, Direction.NORTH, WallTrack.CLIMB));
		}
		updatePower(level, o, 7);
		return cart(level, new Vec3(o.getX() + 0.5, o.getY() + 0.65 - 0.35, o.getZ() + 0.45), Vec3.ZERO);
	}

	private static BlockState wall(WallRailBlock block, Direction facing, WallTrack track) {
		return block.defaultBlockState().setValue(WallRailBlock.FACING, facing).setValue(WallRailBlock.TRACK, track);
	}

	/** Track is set without its neighbours being told, so nothing reshapes what the test laid out. */
	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
	}

	private static void updatePower(ServerLevel level, BlockPos o, int size) {
		for (BlockPos pos : BlockPos.betweenClosed(o.offset(-1, -1, -1), o.offset(1, size, size))) {
			BlockState state = level.getBlockState(pos);
			if (MountedRails.isMounted(state)) level.neighborChanged(pos, state.getBlock(), null);
		}
	}

	private static void clear(ServerLevel level, BlockPos o, int up, int south) {
		for (BlockPos pos : BlockPos.betweenClosed(o.offset(-2, -2, -2), o.offset(2, up, south + 1))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private static int cart(ServerLevel level, Vec3 at, Vec3 motion) {
		AbstractMinecart cart = EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND);
		if (cart == null) throw new AssertionError("could not make a minecart");
		cart.snapTo(at.x, at.y, at.z, 0F, 0F);
		cart.setDeltaMovement(motion);
		level.addFreshEntity(cart);
		return cart.getId();
	}

	private static Vec3[] positions(ServerLevel level, int[] carts) {
		Vec3[] out = new Vec3[carts.length];
		for (int i = 0; i < carts.length; i++) {
			var cart = level.getEntity(carts[i]);
			if (cart == null) throw new AssertionError("cart " + i + " is gone");
			out[i] = cart.position();
		}
		return out;
	}

	private static <T> T ask(Smoke.Session session, Function<MinecraftServer, T> question) {
		AtomicReference<T> answer = new AtomicReference<>();
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		session.onServer(server -> {
			try {
				answer.set(question.apply(server));
			} catch (Throwable e) {
				thrown.set(e);
			}
		});
		if (thrown.get() != null) throw new AssertionError(thrown.get().getMessage(), thrown.get());
		return answer.get();
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
