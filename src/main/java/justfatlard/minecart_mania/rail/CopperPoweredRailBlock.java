package justfatlard.minecart_mania.rail;

import net.minecraft.world.level.block.PoweredRailBlock;

/**
 * A powered rail of waxed copper: twice vanilla where gold is four times.
 *
 * <p>Vanilla's boost and brake logic asks whether the block is gold, by identity, so this
 * class alone would be a powered rail that neither pushes nor stops; {@code RailSpeedMixin}
 * widens that question to any powered rail.
 */
public class CopperPoweredRailBlock extends PoweredRailBlock {
	public CopperPoweredRailBlock(Properties properties) {
		super(properties);
	}
}
