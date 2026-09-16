package justfatlard.minecart_mania.cart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import justfatlard.minecart_mania.Main;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Carts joined by chains, so one pulls or pushes the next.
 *
 * <p>Chain in hand: click one cart, then another, and they are linked. The chain is spent and
 * the carts snap to a little over a cart's length apart. From then on a tick's worth of
 * physics keeps them there: stretched past rest they pull together, pushed inside it they
 * push apart, and their speeds are blended so a train moves as one thing. A cart takes a chain
 * at either end and no more, so a line of them is a train and a fork is not. Sneak and click a
 * cart with a chain and it comes free of everything it was chained to, the chains dropping at
 * your feet; a plain click with a chain used to do that, and a plain click is what you were
 * doing to add the next cart.
 *
 * <p>The links are stored on both carts and survive a restart. A partner whose chunk is not
 * loaded is simply not pulled on that tick.
 */
public final class ChainLinks {
	private ChainLinks() {}

	/** The one partner a cart used to have, read once and carried over. */
	private static final AttachmentType<UUID> PARTNER = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "chain_partner"), UUIDUtil.CODEC);
	private static final AttachmentType<List<UUID>> LINKS = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "chain_links"), UUIDUtil.CODEC.listOf());
	/** An end at each end. */
	private static final int LINKS_MOST = 2;

	/** The cart a player clicked first, waiting for the second. */
	private static final Map<UUID, UUID> pending = new ConcurrentHashMap<>();

	/**
	 * Centre to centre, in blocks. A cart is a block long, near enough, so anything under one
	 * has the pair inside each other; the chain lets them ride between SLACK and REST, a
	 * quarter block of air at the closest, and pushes them apart from any nearer. They used to
	 * be allowed to close right up to a cart's length, and a train braking or bunching at a
	 * curve had them a good way through each other before the push took hold.
	 */
	static final double REST = 1.45;
	static final double STRETCHED = REST + 0.35;
	/**
	 * How far off REST the pair may ride before the link takes hold: a chain's few links of
	 * play, not a spring's. Past it the gap is closed hard, half of what is left every tick.
	 */
	private static final double GIVE = 0.05;
	private static final double RESTORE = 0.5;
	/** The most closing speed the link puts on a pair in one tick, so a chain hauls and does not fling. */
	private static final double FIX_MOST = 0.25;
	/**
	 * A cart with a chain held to it and nothing yet on the other end is on a leash: it comes
	 * along after the hand holding the chain, slowly, once the chain is out to its length. The
	 * pull is gentle and the pace a walk, so a cart can be led round a yard and not flung.
	 */
	private static final double LEASH_LENGTH = 2.5;
	private static final double LEASH_PULL = 0.015;
	private static final double LEASH_PACE = 0.1;
	private static final double LEASH_SLIPS = 32.0;
	/** How fast the second cart may close on the first when the chain is first put on. */
	private static final double SNAP_MOST = 0.12;
	private static final double REACH = 16.0;

	public static void register() {
		UseEntityCallback.EVENT.register(ChainLinks::onUse);
		ServerTickEvents.END_SERVER_TICK.register(ChainLinks::tickLeashes);
	}

	private static boolean isChain(ItemStack stack) {
		return stack.is(Items.IRON_CHAIN) || Items.COPPER_CHAIN.asList().contains(stack.getItem());
	}

	/** Every cart on a leash: led after its holder while the chain is in hand and in reach. */
	private static void tickLeashes(MinecraftServer server) {
		for (Map.Entry<UUID, UUID> held : List.copyOf(pending.entrySet())) {
			UUID cartId = held.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(held.getKey());
			AbstractMinecart cart = player != null && player.level() instanceof ServerLevel level
				&& level.getEntity(cartId) instanceof AbstractMinecart found && found.isAlive() ? found : null;
			if (cart == null || player.distanceTo(cart) > LEASH_SLIPS) {
				pending.remove(held.getKey());
				ChainVisual.removeLeash(cartId);
				if (player != null && cart != null) player.sendOverlayMessage(Component.literal("The chain slipped from the cart"));
				continue;
			}
			if (!isChain(player.getMainHandItem()) && !isChain(player.getOffhandItem())) {
				ChainVisual.hideLeash(cartId);
				continue;
			}
			double d = player.distanceTo(cart);
			if (d > LEASH_LENGTH) {
				Vec3 toward = player.position().subtract(cart.position());
				toward = new Vec3(toward.x, 0.0, toward.z);
				if (toward.lengthSqr() > 1.0E-4) {
					Vec3 next = cart.getDeltaMovement().add(toward.normalize().scale(LEASH_PULL));
					double pace = next.horizontalDistance();
					if (pace > LEASH_PACE) next = new Vec3(next.x * LEASH_PACE / pace, next.y, next.z * LEASH_PACE / pace);
					cart.setDeltaMovement(next);
				}
			}
			ChainVisual.leash(cart, player, d);
		}
	}

	/** Every cart this one is chained to, at most one at each end. */
	public static List<UUID> linksOf(AbstractMinecart cart) {
		List<UUID> links = cart.getAttached(LINKS);
		if (links != null) return links;
		UUID old = cart.getAttached(PARTNER);
		if (old == null) return List.of();
		cart.removeAttached(PARTNER);
		cart.setAttached(LINKS, List.of(old));
		return List.of(old);
	}

	public static boolean linked(AbstractMinecart cart, AbstractMinecart other) {
		return linksOf(cart).contains(other.getUUID());
	}

	private static void addLink(AbstractMinecart cart, UUID other) {
		List<UUID> links = new ArrayList<>(linksOf(cart));
		if (!links.contains(other)) links.add(other);
		cart.setAttached(LINKS, List.copyOf(links));
	}

	private static void dropLink(AbstractMinecart cart, UUID other) {
		List<UUID> links = new ArrayList<>(linksOf(cart));
		links.remove(other);
		if (links.isEmpty()) cart.removeAttached(LINKS);
		else cart.setAttached(LINKS, List.copyOf(links));
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity target, EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !(target instanceof AbstractMinecart cart)) return InteractionResult.PASS;
		ItemStack held = player.getMainHandItem();
		if (!isChain(held)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

		if (player.isShiftKeyDown()) {
			if (linksOf(cart).isEmpty()) return InteractionResult.PASS;
			unlink(server, cart, true);
			player.sendOverlayMessage(Component.literal("Cart unchained"));
			return InteractionResult.SUCCESS;
		}
		if (linksOf(cart).size() >= LINKS_MOST) {
			player.sendOverlayMessage(Component.literal("This cart is chained at both ends"));
			return InteractionResult.SUCCESS;
		}

		UUID first = pending.get(player.getUUID());
		Entity other = first == null ? null : server.getEntity(first);
		if (!(other instanceof AbstractMinecart head) || head == cart || !head.isAlive()
				|| linksOf(head).size() >= LINKS_MOST || linked(head, cart) || head.distanceTo(cart) > REACH) {
			if (first != null && !first.equals(cart.getUUID())) ChainVisual.removeLeash(first);
			pending.put(player.getUUID(), cart.getUUID());
			player.sendOverlayMessage(Component.translatableWithFallback("minecart-mania-justfatlard.chain.held",
				"Chain held to this cart - click the next, or lead it"));
			return InteractionResult.SUCCESS;
		}

		pending.remove(player.getUUID());
		ChainVisual.removeLeash(first);
		addLink(head, cart.getUUID());
		addLink(cart, head.getUUID());
		if (!player.isCreative()) held.shrink(1);
		snap(head, cart);
		server.playSound(null, cart.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 0.9F);
		player.sendOverlayMessage(Component.literal("Carts chained"));
		return InteractionResult.SUCCESS;
	}

	/** The second cart takes the first's speed and closes to rest, so the pair starts as a train. */
	private static void snap(AbstractMinecart head, AbstractMinecart tail) {
		tail.setDeltaMovement(head.getDeltaMovement());
		Vec3 gap = tail.position().subtract(head.position());
		double d = gap.length();
		if (d > REST && d < REACH) {
			double closing = Math.min(SNAP_MOST, (d - REST) * 0.5);
			tail.setDeltaMovement(tail.getDeltaMovement().subtract(gap.normalize().scale(closing)));
		}
	}

	/** This cart free of every chain on it, one chain dropped for each if asked. */
	public static void unlink(ServerLevel level, AbstractMinecart cart, boolean dropChain) {
		for (UUID other : List.copyOf(linksOf(cart))) {
			unlinkOne(level, cart, other, dropChain);
		}
	}

	/** One chain off this cart. */
	private static void unlinkOne(ServerLevel level, AbstractMinecart cart, UUID otherId, boolean dropChain) {
		dropLink(cart, otherId);
		ChainVisual.remove(cart.getUUID(), otherId);
		if (level.getEntity(otherId) instanceof AbstractMinecart other) dropLink(other, cart.getUUID());
		if (dropChain) {
			level.addFreshEntity(new ItemEntity(level, cart.getX(), cart.getY() + 0.5, cart.getZ(), new ItemStack(Items.IRON_CHAIN)));
		}
	}

	/** A chained cart is being destroyed: the pair parts and the chain drops where it was. */
	/** Part these two, and this pair only. */
	public static void unlinkPair(ServerLevel level, AbstractMinecart cart, UUID other, boolean dropChain) {
		if (linksOf(cart).contains(other)) unlinkOne(level, cart, other, dropChain);
	}

	public static void broken(ServerLevel level, AbstractMinecart cart) {
		if (!linksOf(cart).isEmpty()) unlink(level, cart, true);
	}

	/** Once a tick per cart, server side. Each link is worked once, from the cart with the lower id. */
	public static void tick(AbstractMinecart cart) {
		if (!(cart.level() instanceof ServerLevel level)) return;
		for (UUID partnerId : List.copyOf(linksOf(cart))) {
			if (!(level.getEntity(partnerId) instanceof AbstractMinecart partner)) {
				// Not here: unloaded, and kept for when it comes back. A partner that was
				// destroyed parted from this cart itself on its way out, so it never shows up here.
				ChainVisual.remove(cart.getUUID(), partnerId);
				continue;
			}
			if (!partner.isAlive()) {
				unlinkOne(level, cart, partnerId, true);
				continue;
			}
			if (cart.getId() > partner.getId()) continue;

			Vec3 gap = partner.position().subtract(cart.position());
			double d = gap.length();
			if (d > REACH * 2) {
				unlinkOne(level, cart, partnerId, true);
				continue;
			}
			// A rod, not a spring. Along the chain the two move at one speed, the mean of
			// their own, and whatever the gap is off its length is closed on top of that. Across
			// the chain each keeps its own way, which on a curve is its own rail. A spring with
			// a soft pull and a wide dead band had them surging back and forth on every start
			// and stop.
			if (d > 1.0E-4) {
				Vec3 dir = gap.scale(1.0 / d);
				double error = Math.abs(d - REST) <= GIVE ? 0.0 : d - REST;
				double along = cart.getDeltaMovement().dot(dir);
				double partnerAlong = partner.getDeltaMovement().dot(dir);
				double mean = (along + partnerAlong) / 2.0;
				double fix = Math.clamp(error * RESTORE, -FIX_MOST, FIX_MOST);
				cart.setDeltaMovement(cart.getDeltaMovement().add(dir.scale(mean + fix / 2.0 - along)));
				partner.setDeltaMovement(partner.getDeltaMovement().add(dir.scale(mean - fix / 2.0 - partnerAlong)));
			}

			ChainVisual.update(cart, partner, d);
		}
	}
}
