package justfatlard.minecart_mania.rail;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import justfatlard.minecart_mania.Main;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rails on walls and ceilings: which there are, how track joins from block to block whatever
 * it is laid on, and laying it.
 *
 * <p>Joining is one rule for every rail, floor or mounted: two blocks of track join where an
 * end of one is the same point as an end of the other, with the cart on the same side of
 * both. That is what keeps a ceiling rail from joining the floor rail on the block above it,
 * whose ends sit at exactly the same points.
 */
public final class MountedRails {
	private MountedRails() {}

	/** A floor rail's two mounted forms. */
	public record Kind(Block floor, CeilingRailBlock ceiling, WallRailBlock wall) {}

	private static final Map<Block, Kind> BY_FLOOR = new LinkedHashMap<>();
	/** Every mounted block, by registry name. */
	public static final Map<String, MountedRailBlock> BLOCKS = new LinkedHashMap<>();

	static {
		kind("rail", Blocks.RAIL, false, SoundType.METAL, 0.7F);
		kind("powered_rail", Blocks.POWERED_RAIL, true, SoundType.METAL, 0.7F);
		kind("wooden_rail", Rails.WOODEN_RAIL, false, SoundType.WOOD, 0.5F);
		kind("copper_powered_rail", Rails.COPPER_POWERED_RAIL, true, SoundType.COPPER, 0.7F);
	}

	private static void kind(String name, Block floor, boolean powered, SoundType sound, float strength) {
		CeilingRailBlock ceiling = powered
			? new CeilingRailBlock.Powered(floor, properties("ceiling_" + name, sound, strength))
			: new CeilingRailBlock(floor, properties("ceiling_" + name, sound, strength));
		WallRailBlock wall = powered
			? new WallRailBlock.Powered(floor, properties("wall_" + name, sound, strength))
			: new WallRailBlock(floor, properties("wall_" + name, sound, strength));
		BY_FLOOR.put(floor, new Kind(floor, ceiling, wall));
		BLOCKS.put("ceiling_" + name, ceiling);
		BLOCKS.put("wall_" + name, wall);
	}

	private static BlockBehaviour.Properties properties(String name, SoundType sound, float strength) {
		return BlockBehaviour.Properties.of()
			.setId(ResourceKey.create(Registries.BLOCK, Main.id(name)))
			.noCollision().strength(strength).sound(sound);
	}

	public static void register() {
		UseBlockCallback.EVENT.register(MountedRails::onUse);
	}

	public static boolean isMounted(BlockState state) {
		return state.getBlock() instanceof MountedRailBlock;
	}

	/** Whether this is any rail at all, floor or mounted. */
	public static boolean isTrack(BlockState state) {
		return Rails.isRail(state) || isMounted(state);
	}

	// ---- Joining ----

	/** The ends of the track at {@code pos}, in world coordinates, or none if there is no track. */
	public static List<TrackPath.End> ends(BlockState state, BlockPos pos) {
		if (state.getBlock() instanceof MountedRailBlock mounted) {
			return mounted.path(state).offset(pos).ends();
		}
		if (state.getBlock() instanceof BaseRailBlock rail) {
			Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(state.getValue(rail.getShapeProperty()));
			return List.of(floorEnd(exits.getFirst()).offset(pos), floorEnd(exits.getSecond()).offset(pos));
		}
		return List.of();
	}

	private static final Vec3 FLOOR_UP = new Vec3(0, 1, 0);

	/** A floor rail's end: the middle of a face's bottom edge, or its top edge going uphill. */
	private static TrackPath.End floorEnd(Vec3i exit) {
		return new TrackPath.End(new Vec3(0.5 + exit.getX() * 0.5, exit.getY(), 0.5 + exit.getZ() * 0.5), FLOOR_UP);
	}

	/** Every block whose corner or face this point lies on, which is every block an end there could join. */
	static List<BlockPos> touching(Vec3 point) {
		List<BlockPos> out = new ArrayList<>(8);
		int[][] axes = {options(point.x), options(point.y), options(point.z)};
		for (int x : axes[0]) for (int y : axes[1]) for (int z : axes[2]) out.add(new BlockPos(x, y, z));
		return out;
	}

	private static int[] options(double v) {
		long whole = Math.round(v);
		if (Math.abs(v - whole) < 1.0E-6) return new int[] {(int) whole - 1, (int) whole};
		return new int[] {(int) Math.floor(v)};
	}

	/** The block of track this end runs on into, as it stands now, or null if it runs off the end. */
	public static @Nullable BlockPos joinedAt(Level level, BlockPos pos, TrackPath.End end) {
		for (BlockPos there : touching(end.point())) {
			if (there.equals(pos)) continue;
			for (TrackPath.End theirs : ends(level.getBlockState(there), there)) {
				if (theirs.meets(end)) return there;
			}
		}
		return null;
	}

	/** The blocks of track this one runs on into. */
	public static List<BlockPos> joinedNeighbours(Level level, BlockPos pos) {
		List<BlockPos> out = new ArrayList<>(2);
		for (TrackPath.End end : ends(level.getBlockState(pos), pos)) {
			BlockPos there = joinedAt(level, pos, end);
			if (there != null) out.add(there);
		}
		return out;
	}

	private static int joinedCount(Level level, BlockPos pos, BlockState state) {
		int count = 0;
		for (TrackPath.End end : ends(state, pos)) {
			if (joinedAt(level, pos, end) != null) count++;
		}
		return count;
	}

	/** Whether some neighbour could be turned to meet this end, whatever shape it holds now. */
	static boolean couldJoin(Level level, BlockPos pos, TrackPath.End end) {
		for (BlockPos there : touching(end.point())) {
			if (there.equals(pos)) continue;
			for (BlockState shape : shapes(level.getBlockState(there))) {
				for (TrackPath.End theirs : ends(shape, there)) {
					if (theirs.meets(end)) return true;
				}
			}
		}
		return false;
	}

	/** Every shape this block of track could be turned to without moving it to another face. */
	public static List<BlockState> shapes(BlockState state) {
		List<BlockState> out = new ArrayList<>();
		if (state.getBlock() instanceof CeilingRailBlock) {
			for (RailShape shape : CeilingRailBlock.TRACK.getPossibleValues()) out.add(state.setValue(CeilingRailBlock.TRACK, shape));
		} else if (state.getBlock() instanceof WallRailBlock) {
			for (WallTrack track : WallTrack.values()) out.add(state.setValue(WallRailBlock.TRACK, track));
		} else if (state.getBlock() instanceof BaseRailBlock rail) {
			for (RailShape shape : rail.getShapeProperty().getPossibleValues()) out.add(state.setValue(rail.getShapeProperty(), shape));
		}
		return out;
	}

	// ---- Laying ----

	/**
	 * A rail held against the underside or the side of a solid block goes on it. Against
	 * another rail, or a face a torch could not stand on, it is laid as before: a floor rail
	 * floating there, which is how a bridge is built out from its last span.
	 */
	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || player.isSpectator()) return InteractionResult.PASS;
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem item)) return InteractionResult.PASS;
		Kind kind = BY_FLOOR.get(item.getBlock());
		Direction face = hit.getDirection();
		if (kind == null || face == Direction.UP) return InteractionResult.PASS;

		BlockPos clicked = hit.getBlockPos();
		BlockState against = level.getBlockState(clicked);
		if (isTrack(against) || !against.isFaceSturdy(level, clicked, face)) return InteractionResult.PASS;
		BlockPos at = clicked.relative(face);
		if (!level.getBlockState(at).canBeReplaced() || !level.mayInteract(player, at) || !player.mayUseItemAt(at, face, stack)) {
			return InteractionResult.PASS;
		}

		BlockState laid = best(level, at, face == Direction.DOWN
			? ceilingPreferences(kind, player.getDirection())
			: wallPreferences(kind, face.getOpposite(), Math.abs(player.getXRot()) > 45));
		level.setBlock(at, laid, Block.UPDATE_ALL);
		joinNeighbours(server, at);

		SoundType sound = laid.getSoundType();
		level.playSound(null, at, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
		level.gameEvent(player, GameEvent.BLOCK_PLACE, at);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	/** Every shape a ceiling rail could take, the straight along the player's facing first. */
	private static List<BlockState> ceilingPreferences(Kind kind, Direction looking) {
		List<BlockState> out = new ArrayList<>();
		BlockState base = kind.ceiling().defaultBlockState();
		out.add(base.setValue(CeilingRailBlock.TRACK, looking.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH));
		out.addAll(shapes(base));
		return out;
	}

	/** Every shape a wall rail could take, up the wall first when the player looks steeply, along it otherwise. */
	private static List<BlockState> wallPreferences(Kind kind, Direction facing, boolean steep) {
		List<BlockState> out = new ArrayList<>();
		BlockState base = kind.wall().defaultBlockState().setValue(WallRailBlock.FACING, facing);
		out.add(base.setValue(WallRailBlock.TRACK, steep ? WallTrack.CLIMB : WallTrack.ALONG));
		out.add(base.setValue(WallRailBlock.TRACK, steep ? WallTrack.ALONG : WallTrack.CLIMB));
		out.addAll(shapes(base));
		return out;
	}

	/**
	 * The shape that meets the most track: an end that meets a neighbour as it lies counts
	 * double one that meets a neighbour which could be turned to it. Ties go to the earliest in
	 * {@code preferences}.
	 */
	private static BlockState best(Level level, BlockPos pos, List<BlockState> preferences) {
		BlockState best = preferences.getFirst();
		int bestScore = -1;
		for (BlockState option : preferences) {
			int score = 0;
			for (TrackPath.End end : ends(option, pos)) {
				if (joinedAt(level, pos, end) != null) score += 2;
				else if (couldJoin(level, pos, end)) score += 1;
			}
			if (score > bestScore) {
				best = option;
				bestScore = score;
			}
		}
		return best;
	}

	/**
	 * Turn the track around a newly laid rail to meet it, where that joins a neighbour up more
	 * than it was. A pinned rail keeps its shape. A floor rail below the foot of a climb is
	 * turned into the bend up onto the wall, which is the one way a floor rail changes block.
	 */
	public static void joinNeighbours(ServerLevel level, BlockPos laid) {
		for (TrackPath.End end : ends(level.getBlockState(laid), laid)) {
			for (BlockPos there : touching(end.point())) {
				if (!there.equals(laid)) reshape(level, there);
			}
		}
	}

	/** Turn this block of track to the shape that joins most, if that joins more than it does now. */
	public static void reshape(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!isTrack(state) || RailPins.isPinned(level, pos)) return;
		List<BlockState> options;
		if (isMounted(state)) {
			options = shapes(state);
		} else if (state.getBlock() instanceof BaseRailBlock && !(state.getBlock() instanceof SwitchingRailBlock)
				&& BY_FLOOR.containsKey(state.getBlock())) {
			options = new ArrayList<>();
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				options.add(BY_FLOOR.get(state.getBlock()).wall().defaultBlockState()
					.setValue(WallRailBlock.FACING, facing).setValue(WallRailBlock.TRACK, WallTrack.FROM_FLOOR));
			}
		} else {
			return;
		}
		int now = joinedCount(level, pos, state);
		BlockState best = state;
		int bestCount = now;
		for (BlockState option : options) {
			int count = joinedCount(level, pos, option);
			if (count > bestCount) {
				best = option;
				bestCount = count;
			}
		}
		if (best != state) level.setBlock(pos, best, Block.UPDATE_ALL);
	}

	/** How a mounted shape reads in a message: "up the wall", "ceiling north_south". */
	public static String shapeName(BlockState state) {
		if (state.getBlock() instanceof CeilingRailBlock) return "ceiling " + state.getValue(CeilingRailBlock.TRACK).getSerializedName();
		return switch (state.getValue(WallRailBlock.TRACK)) {
			case ALONG -> "along the wall";
			case CLIMB -> "up the wall";
			case UP_LEFT -> "left to up";
			case UP_RIGHT -> "right to up";
			case DOWN_LEFT -> "left to down";
			case DOWN_RIGHT -> "right to down";
			case FROM_FLOOR -> "floor to wall";
			case TO_CEILING -> "wall to ceiling";
		};
	}

	/** The mounted forms of a floor rail, or null if it has none. */
	public static @Nullable Kind kindOf(Block floor) {
		return BY_FLOOR.get(floor);
	}
}
