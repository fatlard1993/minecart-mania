package justfatlard.minecart_mania.rail;

import justfatlard.minecart_mania.Main;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rails, and how fast each lets a cart go.
 *
 * <p>Vanilla has one speed for every rail, eight metres a second, and it lives in the cart.
 * Here the speed lives in the track, which is where a railway keeps it: wood is the vanilla
 * eight, iron is a step up, copper power doubles vanilla and gold power quadruples it. The
 * detector, activator, crossing and junction are iron-made and run at iron's pace.
 */
public final class Rails {
	private Rails() {}

	/** Metres per second. Vanilla is 8; the game works in blocks per tick, so twenty of these make one of those. */
	public static final double WOOD = 8.0;
	public static final double IRON = 12.0;
	public static final double COPPER_POWERED = 16.0;
	public static final double GOLD_POWERED = 32.0;

	public static final WoodenRailBlock WOODEN_RAIL = new WoodenRailBlock(rail("wooden_rail", SoundType.WOOD, 0.5F));
	public static final CopperPoweredRailBlock COPPER_POWERED_RAIL = new CopperPoweredRailBlock(rail("copper_powered_rail", SoundType.COPPER, 0.7F));
	public static final CrossRailBlock CROSS_RAIL = new CrossRailBlock(rail("cross_rail", SoundType.METAL, 0.7F));
	public static final TeeRailBlock TEE_RAIL = new TeeRailBlock(rail("tee_rail", SoundType.METAL, 0.7F));

	private static BlockBehaviour.Properties rail(String name, SoundType sound, float strength) {
		return BlockBehaviour.Properties.of()
			.setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Main.MOD_ID, name)))
			.noCollision().strength(strength).sound(sound);
	}

	/** The speed a cart may reach on this block, or {@link #IRON} for any rail this mod does not know. */
	public static double maxSpeed(BlockState state) {
		Block block = state.getBlock();
		if (block == WOODEN_RAIL) return WOOD;
		if (block == COPPER_POWERED_RAIL) return COPPER_POWERED;
		if (block == Blocks.POWERED_RAIL) return GOLD_POWERED;
		return IRON;
	}

	public static boolean isRail(BlockState state) {
		return BaseRailBlock.isRail(state);
	}
}
