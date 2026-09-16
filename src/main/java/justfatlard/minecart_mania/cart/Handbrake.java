package justfatlard.minecart_mania.cart;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.minecart_mania.Main;
import justfatlard.pandorical.api.KeybindApi;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

/**
 * Hold a key to drag the cart down to a stop.
 *
 * <p>A rider in vanilla has no say in the cart's speed once it is moving: the track decides,
 * and the only brake is the end of the line. This is a hand on the lever: while the key is
 * held the cart sheds speed every tick, sparks off the rail and grinds, and lets go the moment
 * the key does. Pandorical carries the key; a vanilla client has no lever to pull.
 */
public final class Handbrake {
	private Handbrake() {}

	/** Pandorical's second pool slot starts on B; naming it claims that slot. */
	private static final int KEY_B = KeybindApi.letter('B');
	/** Speed kept per tick while braking: to a halt from a full gold line in about a second and a half. */
	private static final double DRAG = 0.86;
	private static final double SPARKS_ABOVE = 2.0;

	private static final Set<UUID> braking = ConcurrentHashMap.newKeySet();

	public static void register() {
		PandoricalApi.keybinds().register(Main.MOD_ID + ":handbrake", KEY_B, "Minecart Handbrake",
			new KeybindApi.KeybindHandler() {
				@Override
				public void onPress(ServerPlayer player) {
					if (player.getVehicle() instanceof AbstractMinecart) braking.add(player.getUUID());
				}

				@Override
				public void onRelease(ServerPlayer player) {
					braking.remove(player.getUUID());
				}
			});
	}

	public static void forget(UUID player) {
		braking.remove(player);
	}

	/** Once a tick per cart, server side: any rider with the lever pulled slows it. */
	public static void tick(AbstractMinecart cart) {
		if (braking.isEmpty() || !(cart.level() instanceof ServerLevel level)) return;
		boolean pulled = false;
		for (Entity rider : cart.getPassengers()) {
			if (rider instanceof Player player && braking.contains(player.getUUID())) pulled = true;
		}
		if (!pulled) return;

		Vec3 motion = cart.getDeltaMovement();
		double speed = motion.horizontalDistance() * 20.0;
		cart.setDeltaMovement(motion.x * DRAG, motion.y, motion.z * DRAG);
		if (speed < SPARKS_ABOVE) return;

		Vec3 pos = cart.position();
		level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.1, pos.z, 3, 0.4, 0.05, 0.4, 0.02);
		if (level.getGameTime() % 4 == 0) {
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL,
				0.4F, 0.6F + (float) Math.min(speed / 32.0, 0.8));
		}
	}
}
