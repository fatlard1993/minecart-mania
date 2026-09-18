package justfatlard.minecart_mania;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The advancements this mod hands out.
 *
 * <p>Measured by what came out of the wall rather than by how many carts went in. A single cart at
 * a standstill takes a bite; a volley at a rail's full pace opens a corridor you can walk down, and
 * only the second is a tunnel. Counting the cleared blocks says which happened without the goal
 * having to know anything about volleys, shares or headings.
 */
public final class Awards {
	private Awards() {}

	/** Blocks out of one blast that make a tunnel rather than a dent. */
	private static final int A_TUNNEL = 150;

	/** How far from the mouth whoever set it off can be standing. */
	private static final double WATCHING = 48.0;

	public static void bored(ServerLevel level, BlockPos origin, int cleared) {
		if (cleared < A_TUNNEL) return;
		Player nearest = level.getNearestPlayer(origin.getX() + 0.5, origin.getY() + 0.5,
			origin.getZ() + 0.5, WATCHING, false);
		if (nearest instanceof ServerPlayer player) award(player, "tunnel");
	}

	private static void award(ServerPlayer player, String path) {
		if (player.level().getServer() == null) return;
		AdvancementHolder holder = player.level().getServer().getAdvancements()
			.get(Identifier.fromNamespaceAndPath(Main.MOD_ID, path));
		if (holder == null) return;

		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		if (progress.isDone()) return;
		for (String criterion : progress.getRemainingCriteria()) {
			player.getAdvancements().award(holder, criterion);
		}
	}
}
