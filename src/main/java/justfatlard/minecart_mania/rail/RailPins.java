package justfatlard.minecart_mania.rail;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import justfatlard.minecart_mania.Main;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

/**
 * Which rails a player has shaped by hand, per world.
 *
 * <p>Kept beside the world rather than in the block, because a vanilla rail's states are fixed
 * and every client knows them: one more property on {@code minecraft:rail} would renumber every
 * block state a vanilla client thinks it knows. A pin is dropped the moment the block there is
 * no longer a rail, so a broken and relaid track starts unpinned.
 */
public final class RailPins {
	private RailPins() {}

	private static final AttachmentType<List<Long>> PINS = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "rail_pins"), Codec.LONG.listOf());

	public static boolean isPinned(ServerLevel level, BlockPos pos) {
		List<Long> pins = level.getAttached(PINS);
		if (pins == null || !pins.contains(pos.asLong())) return false;
		if (!Rails.isRail(level.getBlockState(pos))) {
			unpin(level, pos);
			return false;
		}
		return true;
	}

	public static void pin(ServerLevel level, BlockPos pos) {
		List<Long> pins = new ArrayList<>(level.getAttachedOrElse(PINS, List.of()));
		if (!pins.contains(pos.asLong())) pins.add(pos.asLong());
		level.setAttached(PINS, pins);
	}

	public static void unpin(ServerLevel level, BlockPos pos) {
		List<Long> pins = new ArrayList<>(level.getAttachedOrElse(PINS, List.of()));
		if (pins.remove(Long.valueOf(pos.asLong()))) level.setAttached(PINS, pins);
	}
}
