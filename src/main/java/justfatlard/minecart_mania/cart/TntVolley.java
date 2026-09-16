package justfatlard.minecart_mania.cart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.phys.Vec3;

/**
 * A chain of TNT carts sent at a face goes off as one charge.
 *
 * <p>The first cart over the activator rail is the lead; the TNT carts chained to it, up to
 * six in all, are its volley. The chain is walked cart to cart and stops at the first that is
 * not TNT, and at the sixth: anything past either is unhooked and left standing on the track.
 * Speed decides how many of those actually answer - a train that crawls onto the rail lights
 * the lead alone, one at the rail's full pace lights all six.
 *
 * <p>Each cart in the volley is flung from where it was to a spot of its own beside the lead,
 * so the whole charge can be seen slapped against the face, and then takes the same nod the
 * lone cart takes, at the fast end of its range. They all go off on the lead's fuse: the lead
 * bores, sized for the number that came, and the rest are spent with it.
 */
public final class TntVolley {
	private TntVolley() {}

	public static final int MOST = 6;
	/** Ticks of the fling from the track to the spread spot, before the nod begins. */
	public static final int FLING_TICKS = 6;
	/** How high the fling arcs at its middle. */
	private static final double ARC = 0.6;

	/** Spread spots by position in the volley, as (across, along, up) from the lead: along runs back from the face. */
	private static final Vec3[] SPOTS = {
		Vec3.ZERO,
		new Vec3(1.0, 0.0, 0.0),
		new Vec3(-1.0, 0.0, 0.0),
		new Vec3(2.0, -0.6, 0.0),
		new Vec3(-2.0, -0.6, 0.0),
		new Vec3(0.0, -1.2, 0.35),
	};

	private record Member(UUID lead, int index, int size, Vec3 start, Vec3 spot, long flungAt) {}

	private static final Map<UUID, Member> members = new ConcurrentHashMap<>();

	/** Whether this cart is in a volley, lead or follower. */
	public static boolean isMember(MinecartTNT cart) {
		return members.containsKey(cart.getUUID());
	}

	/** A follower whose lead is still there to bore for it; a follower left alone bores for itself. */
	public static boolean isFollower(MinecartTNT cart) {
		Member m = members.get(cart.getUUID());
		if (m == null || m.lead.equals(cart.getUUID())) return false;
		if (!(cart.level() instanceof ServerLevel level)) return false;
		return level.getEntity(m.lead) instanceof MinecartTNT lead && !lead.isRemoved();
	}

	/** How many carts the charge is made of: one for a cart on its own. */
	public static int sizeOf(MinecartTNT cart) {
		Member m = members.get(cart.getUUID());
		return m == null ? 1 : m.size;
	}

	/** Ticks the nod waits for the fling; nothing for a cart on its own. */
	public static float tipDelay(MinecartTNT cart) {
		Member m = members.get(cart.getUUID());
		return m == null || m.index == 0 ? 0.0F : FLING_TICKS;
	}

	/** Where the cart rests this tick, on its way to its spot or at it; null for a cart not in a volley. */
	public static Vec3 restingAt(MinecartTNT cart, long now) {
		Member m = members.get(cart.getUUID());
		if (m == null) return null;
		if (m.index == 0) return m.spot;
		double t = Math.clamp((now - m.flungAt) / (double) FLING_TICKS, 0.0, 1.0);
		double eased = 1.0 - (1.0 - t) * (1.0 - t);
		double arc = ARC * Math.sin(Math.PI * t);
		return m.start.lerp(m.spot, eased).add(0.0, arc, 0.0);
	}

	/**
	 * The lead was just lit by the rail: gather its volley and light them too.
	 *
	 * <p>Called from the head of priming. A cart already enrolled - a follower being lit from
	 * here - is left alone, so the walk happens once, from the lead.
	 */
	public static void onPrimed(MinecartTNT lead, DamageSource source, Direction heading, double share) {
		if (isMember(lead) || !(lead.level() instanceof ServerLevel level)) return;

		List<MinecartTNT> chain = walk(level, lead);
		int answering = Math.min(chain.size(), 1 + (int) Math.floor(Math.clamp(share, 0.0, 1.0) * (MOST - 1) + 1.0E-6));
		List<MinecartTNT> volley = chain.subList(0, answering);

		// Whatever is not coming is unhooked from whatever is, and stays where it stands.
		Set<UUID> coming = new HashSet<>();
		for (MinecartTNT cart : volley) coming.add(cart.getUUID());
		List<AbstractMinecart> letGo = new ArrayList<>();
		for (MinecartTNT cart : volley) {
			for (UUID other : List.copyOf(ChainLinks.linksOf(cart))) {
				if (coming.contains(other)) continue;
				if (level.getEntity(other) instanceof AbstractMinecart left) letGo.add(left);
				ChainLinks.unlinkPair(level, cart, other, true);
			}
		}
		halt(level, letGo, coming);

		Direction across = heading.getClockWise();
		Vec3 base = lead.position();
		long now = level.getGameTime();
		for (int i = 0; i < volley.size(); i++) {
			MinecartTNT cart = volley.get(i);
			Vec3 offset = SPOTS[Math.min(i, SPOTS.length - 1)];
			Vec3 spot = base
				.add(Vec3.atLowerCornerOf(across.getUnitVec3i()).scale(offset.x))
				.add(Vec3.atLowerCornerOf(heading.getUnitVec3i()).scale(offset.y))
				.add(0.0, offset.z, 0.0);
			members.put(cart.getUUID(), new Member(lead.getUUID(), i, volley.size(), cart.position(), spot, now));
		}
		for (int i = 1; i < volley.size(); i++) {
			MinecartTNT cart = volley.get(i);
			if (cart instanceof TntLit lit) lit.minecartMania$lightAs(heading, share);
			cart.primeFuse(source);
		}
	}

	/**
	 * Stop what was let go, and everything still chained to it.
	 *
	 * <p>"Stays where it stands" has to be made true. A train let go at speed kept its speed, ran
	 * into the volley pinned at its spots, bounced back, and rolled on over the activator rail a
	 * second time to light another volley nobody asked for. So the rest of the train stops dead,
	 * however long it is - the volley only counts six - and a furnace cart pushing it is set to
	 * Stopped on its panel, where it can be set going again, rather than left driving the rest
	 * into the face.
	 */
	private static void halt(ServerLevel level, List<AbstractMinecart> letGo, Set<UUID> coming) {
		Set<UUID> seen = new HashSet<>(coming);
		Deque<AbstractMinecart> pending = new ArrayDeque<>();
		for (AbstractMinecart cart : letGo) {
			if (seen.add(cart.getUUID())) pending.add(cart);
		}
		while (!pending.isEmpty()) {
			AbstractMinecart cart = pending.poll();
			cart.setDeltaMovement(Vec3.ZERO);
			if (cart instanceof net.minecraft.world.entity.vehicle.minecart.MinecartFurnace furnace) {
				FurnaceControls.Settings settings = FurnaceControls.of(furnace);
				FurnaceControls.set(furnace, new FurnaceControls.Settings(settings.level(), false, settings.reversed()));
				furnace.push = Vec3.ZERO;
			}
			for (UUID other : ChainLinks.linksOf(cart)) {
				if (seen.add(other) && level.getEntity(other) instanceof AbstractMinecart next) pending.add(next);
			}
		}
	}

	/** The TNT carts chained to this one, nearest first, this one at the head; stops at anything else and at six. */
	private static List<MinecartTNT> walk(ServerLevel level, MinecartTNT lead) {
		List<MinecartTNT> found = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		Deque<MinecartTNT> pending = new ArrayDeque<>();
		found.add(lead);
		seen.add(lead.getUUID());
		pending.add(lead);
		while (!pending.isEmpty() && found.size() < MOST) {
			MinecartTNT at = pending.poll();
			for (UUID other : ChainLinks.linksOf(at)) {
				if (!seen.add(other)) continue;
				if (!(level.getEntity(other) instanceof MinecartTNT next) || next.isRemoved() || next.isPrimed()) continue;
				found.add(next);
				pending.add(next);
				if (found.size() >= MOST) break;
			}
		}
		return found;
	}

	/** Nuggets in an ingot. */
	private static final int NUGGETS_PER_INGOT = 9;
	/** What a chain between two carts adds to the pool, at least and at most. */
	private static final int CHAIN_NUGGETS_LEAST = 1;
	private static final int CHAIN_NUGGETS_MOST = 2;

	/**
	 * The lead has bored: the rest of the volley is spent with it, and the leavings are pooled.
	 *
	 * <p>Every cart gives its nuggets, every chain between two of them a nugget or two more -
	 * the chains go into the pool rather than falling as chains - and a charge this big runs
	 * hot enough to forge some of the pool back into ingots: one per cart past the first, and
	 * as many again on a good roll. The lot is dropped where the lead stood.
	 */
	public static void spend(MinecartTNT lead) {
		Member self = members.remove(lead.getUUID());
		if (self == null || !(lead.level() instanceof ServerLevel level)) return;

		List<MinecartTNT> spent = new ArrayList<>();
		spent.add(lead);
		for (Map.Entry<UUID, Member> entry : List.copyOf(members.entrySet())) {
			if (!entry.getValue().lead.equals(lead.getUUID())) continue;
			members.remove(entry.getKey());
			if (level.getEntity(entry.getKey()) instanceof MinecartTNT cart && !cart.isRemoved()) spent.add(cart);
		}

		int pool = 0;
		for (MinecartTNT cart : spent) pool += TntTunnel.nuggetsOf(level);

		Set<UUID> gone = new HashSet<>();
		for (MinecartTNT cart : spent) gone.add(cart.getUUID());
		for (MinecartTNT cart : spent) {
			for (UUID other : List.copyOf(ChainLinks.linksOf(cart))) {
				if (!gone.contains(other)) continue;
				ChainLinks.unlinkPair(level, cart, other, false);
				pool += CHAIN_NUGGETS_LEAST + level.getRandom().nextInt(CHAIN_NUGGETS_MOST - CHAIN_NUGGETS_LEAST + 1);
			}
		}

		int carts = spent.size();
		int ingots = 0;
		int forge = (carts - 1) + level.getRandom().nextInt(carts);
		while (forge-- > 0 && pool >= NUGGETS_PER_INGOT) {
			pool -= NUGGETS_PER_INGOT;
			ingots++;
		}

		Vec3 at = lead.position();
		TntTunnel.scatter(level, at, net.minecraft.world.item.Items.IRON_INGOT, ingots);
		TntTunnel.scatter(level, at, net.minecraft.world.item.Items.IRON_NUGGET, pool);
		for (MinecartTNT cart : spent) {
			if (cart != lead && !cart.isRemoved()) cart.discard();
		}
	}

	/** Forget a cart that is gone by other means. */
	public static void forget(AbstractMinecart cart) {
		members.remove(cart.getUUID());
	}

	/** What the cart's mixin lets a volley set on a follower before it is lit. */
	public interface TntLit {
		void minecartMania$lightAs(Direction heading, double share);
	}
}
