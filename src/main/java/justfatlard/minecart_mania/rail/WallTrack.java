package justfatlard.minecart_mania.rail;

import net.minecraft.util.StringRepresentable;

/**
 * The ways a rail can lie on a wall, named as seen standing in front of the wall facing it.
 *
 * <p>The two bends leave the wall: {@link #FROM_FLOOR} curves from the floor up onto it,
 * {@link #TO_CEILING} from it over onto the ceiling. Between them they are how a cart gets onto
 * a wall or a ceiling at all.
 */
public enum WallTrack implements StringRepresentable {
	ALONG("along"),
	CLIMB("climb"),
	UP_LEFT("up_left"),
	UP_RIGHT("up_right"),
	DOWN_LEFT("down_left"),
	DOWN_RIGHT("down_right"),
	FROM_FLOOR("from_floor"),
	TO_CEILING("to_ceiling");

	private final String name;

	WallTrack(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
