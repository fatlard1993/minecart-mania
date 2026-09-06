#!/usr/bin/env python3
"""Generate Minecart Mania's models and blockstates, and the 3D rail every rail now uses.

Vanilla draws a rail as one flat textured square a pixel off the ground. This rewrites the
two templates every straight rail is built on - rail_flat, and the raised slope - as four
sleepers with two rails standing on them, cut from the same texture at the same columns.
Because vanilla's own rail, powered rail, detector rail and activator rail all inherit those
templates, they pick up the depth without a model of their own being touched; Pandorical
carries the overrides to its clients. Curves are boxes too: a quarter turn drawn as five
short straights meeting at the angles a model element is allowed to turn, with the rails
mitred at each joint so the outer one has no gaps and the inner one no overlaps.

The mod's own rails are then plain vanilla-shaped models pointing at their own textures.

Deterministic, stdlib only. Usage: python3 generate_models.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "minecart-mania-justfatlard"
RES = os.path.join(HERE, "src/main/resources/assets")
MOD = os.path.join(RES, NS)
VANILLA = os.path.join(RES, "minecraft")

# Where the rail texture puts things: sleepers on these rows, rails down these columns.
TIES = [1, 5, 9, 13]
RAILS = [2, 12]
# How far each rail stands from the track's centre line: the rail columns are centred on 3 and 13.
RAIL_OFFSET = 5
TIE_HEIGHT = 1
RAIL_HEIGHT = 1


def tie(z, base_y, rotation=None):
    e = {"from": [1, base_y, z], "to": [15, base_y + TIE_HEIGHT, z + 2],
         "faces": {"up": {"uv": [1, z, 15, z + 2], "texture": "#rail"},
                   "down": {"uv": [1, z + 2, 15, z], "texture": "#rail"},
                   "north": {"uv": [1, z, 15, z + 1], "texture": "#rail"},
                   "south": {"uv": [1, z, 15, z + 1], "texture": "#rail"},
                   # The ends are cut from the middle of the sleeper's own row: row nought, which
                   # they were cut from, is clear between the rails, and a face with nothing on
                   # it is no face at all.
                   "west": {"uv": [7, z, 9, z + 1], "texture": "#rail"},
                   "east": {"uv": [7, z, 9, z + 1], "texture": "#rail"}}}
    if rotation:
        e["rotation"] = rotation
    return e


# How far a sloped rail runs past each face of its block, measured before the tilt.
#
# A box turned forty-five degrees ends in a face cut square to the slope, and the flat rail it
# meets ends in a face cut square to the ground. Where the two meet, one corner of the tilted
# cut pokes past the flat rail and the other stops short of it, and the wedge between reads as
# a notch at the top and bottom of every ramp. Run the sloped rail half a pixel further and the
# whole cut sits inside the flat rail's box, where nothing can see it. The tilt rescales length
# by root two, so half a pixel along the slope is this much along z.
SLOPE_OVERRUN = 0.5 / (2 ** 0.5)


def rail(x, base_y, rotation=None, overrun=0):
    e = {"from": [x, base_y, -overrun], "to": [x + 2, base_y + RAIL_HEIGHT, 16 + overrun],
         "faces": {"up": {"uv": [x, 0, x + 2, 16], "texture": "#rail"},
                   "down": {"uv": [x, 16, x + 2, 0], "texture": "#rail"},
                   "north": {"uv": [x, 0, x + 2, 1], "texture": "#rail"},
                   "south": {"uv": [x, 15, x + 2, 16], "texture": "#rail"},
                   "west": {"uv": [x, 0, x + 2, 16], "texture": "#rail", "rotation": 90},
                   "east": {"uv": [x, 0, x + 2, 16], "texture": "#rail", "rotation": 90}}}
    if rotation:
        e["rotation"] = rotation
    return e


def flat():
    return {"ambientocclusion": False, "textures": {"particle": "#rail"},
            "elements": [tie(z, 0) for z in TIES] + [rail(x, TIE_HEIGHT) for x in RAILS]}


def raised(angle):
    # The same tilt vanilla gives its plane, applied to every box: the whole track leans as one
    rot = {"origin": [8, 9, 8], "axis": "x", "angle": angle, "rescale": True}
    return {"ambientocclusion": False, "textures": {"particle": "#rail"},
            "elements": [tie(z, 8, dict(rot)) for z in TIES]
                        + [rail(x, 8 + TIE_HEIGHT, dict(rot), SLOPE_OVERRUN) for x in RAILS]}


# The diagonal chord, for a curve sitting between two curves that join it. A staircase
# of alternating curves is a straight line at forty-five degrees drawn as a zigzag; the chord
# from the middle of one connected face to the middle of the other is that line's piece of
# this block, and consecutive chords meet end to end. Pandorical clients swap it in when the
# pattern holds (see Pandorical's RailDiagonals); everyone else keeps the curve.
CHORD = 16 / (2 ** 0.5)
# The same gauge and the same sleepers as a straight. Five pixels across a diagonal is seven
# along the face it crosses, so a chord's rails meet the block face two pixels outboard of a
# straight's, and a full sleeper turned forty-five degrees pokes a little past the block's
# corners. Both were once trimmed to sit inside the lines, and the run came out looking like
# narrow-gauge beside the track it belonged to; a track the right width with a small jog where
# it meets the straight reads better than a neat join on a track that looks like a different
# railway.
CHORD_RAIL_OFFSET = RAIL_OFFSET
CHORD_TIE_HALF = 7
DIAGONALS = {  # curve: (chord centre x, chord centre z, element rotation about y)
    "se": (12, 12, -45),
    "sw": (4, 12, 45),
    "nw": (4, 4, -45),
    "ne": (12, 4, 45),
}


def diagonal(cx, cz, angle):
    rot = {"origin": [cx, 1, cz], "axis": "y", "angle": angle, "rescale": False}
    half = CHORD / 2
    elements = []
    for k in (-1, 0, 1):
        z = cz + k * 3.6 - 1
        elements.append({"from": [cx - CHORD_TIE_HALF, 0, z], "to": [cx + CHORD_TIE_HALF, TIE_HEIGHT, z + 2], "rotation": dict(rot),
                         "faces": {"up": {"uv": [3, 5, 13, 7], "texture": "#rail"},
                                   "down": {"uv": [3, 7, 13, 5], "texture": "#rail"},
                                   "north": {"uv": [3, 5, 13, 6], "texture": "#rail"},
                                   "south": {"uv": [3, 5, 13, 6], "texture": "#rail"},
                                   "west": {"uv": [7, 5, 9, 6], "texture": "#rail"},
                                   "east": {"uv": [7, 5, 9, 6], "texture": "#rail"}}})
    for side, x in zip((-1, 1), RAILS):
        # A rail is cut at the block's faces, not at the chord's ends: the one on the outside of
        # the step runs long, face to face, and the one hugging the corner runs short. Boxes the
        # chord's own length tile a run perfectly, since what one leaves undrawn the next draws,
        # but at the run's end the inner rail stopped short of the bend and the outer poked past.
        import math
        a = math.radians(angle)
        ox, oz = side * CHORD_RAIL_OFFSET * math.cos(a), -side * CHORD_RAIL_OFFSET * math.sin(a)
        outer = ox * (8 - cx) + oz * (8 - cz) > 0
        length = CHORD + 2 * CHORD_RAIL_OFFSET if outer else CHORD - 2 * CHORD_RAIL_OFFSET
        rx = round(cx + side * CHORD_RAIL_OFFSET - 1, 3)
        lo, hi = round(cz - length / 2, 3), round(cz + length / 2, 3)
        elements.append({"from": [rx, TIE_HEIGHT, lo], "to": [rx + 2, TIE_HEIGHT + RAIL_HEIGHT, hi],
                         "rotation": dict(rot),
                         "faces": {"up": {"uv": [x, 2, x + 2, 14], "texture": "#rail"},
                                   "down": {"uv": [x, 14, x + 2, 2], "texture": "#rail"},
                                   "north": {"uv": [x, 0, x + 2, 1], "texture": "#rail"},
                                   "south": {"uv": [x, 15, x + 2, 16], "texture": "#rail"},
                                   "west": {"uv": [x, 2, x + 2, 14], "texture": "#rail", "rotation": 90},
                                   "east": {"uv": [x, 2, x + 2, 14], "texture": "#rail", "rotation": 90}}})
    return {"ambientocclusion": False, "textures": {"particle": "#rail"}, "elements": elements}


# The curve: a quarter turn from the middle of the south face to the middle of the east face,
# which is vanilla's south_east and the shape its blockstate turns into the other three.
#
# A model element may only turn in steps of 22.5 degrees, so the turn is a polyline: a short
# stub straight out of each face, and three legs at 22.5, 45 and 67.5 between them. The rails
# ride five pixels either side of that line, and where two legs meet the rails are mitred -
# each cut at the bisector - so the outer rail does not gap and the inner does not pile up.
# Sleepers sit across the line at about the spacing the straight track keeps.
STUB = 1.5
MIDDLE = 3.0
TIE_LENGTH = 14
TIE_AT = (1.0, 4.2, 8.5, 11.7)   # distances along the centre line


def turn_centre_line():
    """The vertices of the polyline, south face to east face, in block pixels."""
    root2 = 2 ** 0.5
    sin, cos = (2 - root2) ** 0.5 / 2, (2 + root2) ** 0.5 / 2   # of 22.5 degrees
    legs = [(sin, -cos), (1 / root2, -1 / root2), (cos, -sin)]
    # The two outer legs share a length that lands the line exactly on the east face.
    outer = (8 - STUB - MIDDLE / root2) / (sin + cos)
    lengths = (outer, MIDDLE, outer)
    points = [(8.0, 16.0), (8.0, 16.0 - STUB)]
    for (dx, dz), length in zip(legs, lengths):
        x, z = points[-1]
        points.append((x + dx * length, z + dz * length))
    points.append((16.0, 8.0))
    return points


def offset_line(points, distance):
    """The same polyline shifted sideways, with mitred corners."""
    dirs = []
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        length = ((x1 - x0) ** 2 + (z1 - z0) ** 2) ** 0.5
        dirs.append(((x1 - x0) / length, (z1 - z0) / length))
    normals = [(dz, -dx) for dx, dz in dirs]
    out = [(points[0][0] + normals[0][0] * distance, points[0][1] + normals[0][1] * distance)]
    for i in range(1, len(points) - 1):
        (ax, az), (bx, bz) = normals[i - 1], normals[i]
        dot = ax * bx + az * bz
        mx, mz = (ax + bx) / (1 + dot), (az + bz) / (1 + dot)
        out.append((points[i][0] + mx * distance, points[i][1] + mz * distance))
    out.append((points[-1][0] + normals[-1][0] * distance, points[-1][1] + normals[-1][1] * distance))
    return out


def turned(faces):
    """The same faces for a box lying along z instead of x.

    A face's picture is pinned to the box's own sides, so a box laid the other way needs its
    long sides and its ends swapped and its top turned a quarter, or the sleeper strip is
    squashed across the width and the rail columns in it land along the sleeper's edges - which
    is exactly the grey edging two of the curve's sleepers wore.
    """
    def quarter(face):
        face = dict(face)
        face["rotation"] = (face.get("rotation", 0) + 90) % 360
        return face
    return {"up": quarter(faces["up"]), "down": quarter(faces["down"]),
            "west": faces["north"], "east": faces["south"],
            "north": faces["west"], "south": faces["east"]}


def box_between(p0, p1, width, y0, y1, faces, faces_along="x"):
    """A box lying along the line from p0 to p1, turned about its own centre.

    An element turns by up to 45 degrees, so a line steeper than that is a box lying along z
    turned the other way rather than a box along x turned too far. Positive angles about y
    swing +x toward -z, which is what the chords already rely on. The faces are given for a box
    lying along {@code faces_along}, and remapped when the box ends up lying the other way.
    """
    (x0, z0), (x1, z1) = p0, p1
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    dx, dz = x1 - x0, z1 - z0
    length = (dx * dx + dz * dz) ** 0.5
    import math
    angle = math.degrees(math.atan2(-dz, dx))    # 0 is +x, 90 is -z
    angle = (angle + 180) % 180                  # a segment has no front
    if angle > 90:
        angle -= 180
    if abs(angle) <= 45.0001:
        frm, to = [cx - length / 2, y0, cz - width / 2], [cx + length / 2, y1, cz + width / 2]
        turn = angle
        along = "x"
    else:
        frm, to = [cx - width / 2, y0, cz - length / 2], [cx + width / 2, y1, cz + length / 2]
        turn = angle - 90 if angle > 0 else angle + 90
        along = "z"
    if along != faces_along:
        faces = turned(faces)
    turn = round(turn / 22.5) * 22.5
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if turn:
        e["rotation"] = {"origin": [round(cx, 3), y0, round(cz, 3)], "axis": "y", "angle": turn, "rescale": False}
    return e


def rail_faces(x, length):
    """A rail's faces for a box lying along z, the way the column runs down the texture."""
    v = min(16, round(length))
    return {"up": {"uv": [x, 0, x + 2, v], "texture": "#rail"},
            "down": {"uv": [x, v, x + 2, 0], "texture": "#rail"},
            "north": {"uv": [x, 0, x + 2, 1], "texture": "#rail"},
            "south": {"uv": [x, 15, x + 2, 16], "texture": "#rail"},
            "west": {"uv": [x, 0, x + 2, v], "texture": "#rail", "rotation": 90},
            "east": {"uv": [x, 0, x + 2, v], "texture": "#rail", "rotation": 90}}


def tie_faces(length):
    """A sleeper's faces for a box lying along x, cut from the sleeper strip to its own length.

    A whole sleeper takes the whole strip, rail crossings and all, and the rails standing on
    it cover those. A short piece - the squares a crossing tucks between another line's
    sleepers - took the whole strip too, squashed, and wore the rail columns as grey flecks;
    it takes its own length out of the middle of the strip instead, which is all wood.
    """
    u0, u1 = round(8 - length / 2, 3), round(8 + length / 2, 3)
    return {"up": {"uv": [u0, 5, u1, 7], "texture": "#rail"},
            "down": {"uv": [u0, 7, u1, 5], "texture": "#rail"},
            "north": {"uv": [u0, 5, u1, 6], "texture": "#rail"},
            "south": {"uv": [u0, 5, u1, 6], "texture": "#rail"},
            "west": {"uv": [7, 5, 9, 6], "texture": "#rail"},
            "east": {"uv": [7, 5, 9, 6], "texture": "#rail"}}


def along(points, distance):
    """The point and direction this far along the polyline."""
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        length = ((x1 - x0) ** 2 + (z1 - z0) ** 2) ** 0.5
        if distance <= length:
            t = distance / length
            return (x0 + (x1 - x0) * t, z0 + (z1 - z0) * t), ((x1 - x0) / length, (z1 - z0) / length)
        distance -= length
    (x0, z0), (x1, z1) = points[-2], points[-1]
    length = ((x1 - x0) ** 2 + (z1 - z0) ** 2) ** 0.5
    return points[-1], ((x1 - x0) / length, (z1 - z0) / length)


def curve_elements(place=lambda p: p, tie_top=TIE_HEIGHT, rail_bottom=TIE_HEIGHT,
                   rail_top=TIE_HEIGHT + RAIL_HEIGHT, sleepers=True, cut=lambda line: [line]):
    """The quarter turn's sleepers and rails, with every point passed through {@code place}.

    Mirroring the points is how the same turn faces another pair of walls; the boxes are built
    from the mirrored line, so their angles come out mirrored too. The heights are parameters
    because a curve laid over a straight sits a hair lower, so that where the two coincide the
    straight's faces win and nothing flickers. Each placed rail line goes through {@code cut},
    which hands back the pieces of it to draw.
    """
    centre = turn_centre_line()
    elements = []
    for distance in (TIE_AT if sleepers else ()):
        (x, z), (dx, dz) = along(centre, distance)
        nx, nz = dz, -dx
        half = TIE_LENGTH / 2
        elements.append(box_between(place((x - nx * half, z - nz * half)), place((x + nx * half, z + nz * half)),
                                    2, 0, tie_top, tie_faces(TIE_LENGTH)))
    for side, x in zip((-1, 1), RAILS):
        for line in cut([place(p) for p in offset_line(centre, side * RAIL_OFFSET)]):
            for p0, p1 in zip(line, line[1:]):
                length = ((p1[0] - p0[0]) ** 2 + (p1[1] - p0[1]) ** 2) ** 0.5
                elements.append(box_between(p0, p1, 2, rail_bottom, rail_top, rail_faces(x, length), "z"))
    return elements


def curved():
    return {"ambientocclusion": False, "textures": {"particle": "#rail"}, "elements": curve_elements()}


# The crossing and the junction, in boxes. Both used to borrow the straight template with a
# composite picture in it, and a template that draws sleepers on rows and rails down columns
# makes hash of a picture with a second rail across it. So each is its own arrangement of the
# same sleepers and rails, cut from the plain iron texture, and the composite pictures are left
# to the item icons that were drawn for them.
#
# Where two lines cross at the same height they would fight over the top face, so the crossing
# line is cut around the through line: its rails stop either side of the rails they cross, and
# its sleepers become squares between the sleepers already there.
TIE_CENTRES = [z + 1 for z in TIES]
RAIL_CENTRES = [x + 1 for x in RAILS]


def straight_tie(centre, lo, hi, along):
    """A sleeper across the line, from lo to hi, lying along x or z."""
    if along == "x":
        return box_between((lo, centre), (hi, centre), 2, 0, TIE_HEIGHT, tie_faces(hi - lo))
    return box_between((centre, lo), (centre, hi), 2, 0, TIE_HEIGHT, tie_faces(hi - lo))


def straight_rail(column, centre, lo, hi, along):
    """A length of rail cut from texture column {@code column}, lying along x or z."""
    faces = rail_faces(column, hi - lo)
    if along == "x":
        return box_between((lo, centre), (hi, centre), 2, TIE_HEIGHT, TIE_HEIGHT + RAIL_HEIGHT, faces, "z")
    return box_between((centre, lo), (centre, hi), 2, TIE_HEIGHT, TIE_HEIGHT + RAIL_HEIGHT, faces, "z")


def between_ties():
    """The gaps between the through line's sleepers, where a crossing sleeper may sit."""
    return [(TIES[i] + 2, TIES[i + 1]) for i in range(len(TIES) - 1)]


def between_rails():
    """The stretches of a crossing rail that are not on top of a through rail."""
    return [(0, RAILS[0]), (RAILS[0] + 2, RAILS[1]), (RAILS[1] + 2, 16)]


def crossing():
    elements = []
    # The through line, north to south, whole.
    for z in TIE_CENTRES:
        elements.append(straight_tie(z, 1, 15, "x"))
    for column, x in zip(RAILS, RAIL_CENTRES):
        elements.append(straight_rail(column, x, 0, 16, "z"))
    # The crossing line, east to west, cut around it.
    for x in TIE_CENTRES:
        for lo, hi in between_ties():
            elements.append(straight_tie(x, lo, hi, "z"))
    for column, z in zip(RAILS, RAIL_CENTRES):
        for lo, hi in between_rails():
            elements.append(straight_rail(column, z, lo, hi, "x"))
    return {"ambientocclusion": False, "textures": {"particle": "#rail", "rail": "minecraft:block/rail"},
            "elements": elements}


# How far below the straight's sleepers and rails the merging curve's sit: enough that where
# the two coincide the straight is drawn on top, too little to see anywhere else.
UNDER = 0.05
# The flangeway: how far short of a rail it crosses the branch's rail stops, either side.
FROG = 1.0


def cut_across(points, lo, hi):
    """The polyline with the part that crosses the band lo..hi in z taken out.

    A line that only ends inside the band is left whole: that is a rail running into the one
    it joins, which a switch's rails do at the points, not a rail crossing another.
    """
    pieces, piece, held = [], [points[0]], None
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        crossings = sorted(t for edge in (lo, hi) if z1 != z0
                           for t in [(edge - z0) / (z1 - z0)] if 0 < t < 1)
        prev = (x0, z0)
        for t in crossings + [1.0]:
            p = (x0 + (x1 - x0) * t, z0 + (z1 - z0) * t)
            if lo < (prev[1] + p[1]) / 2 < hi:
                held = [prev] if held is None else held
                held.append(p)
            else:
                if held is not None:
                    pieces.append(piece)
                    piece, held = [prev], None
                piece.append(p)
            prev = p
    if held is not None:
        piece += held[1:]
    pieces.append(piece)
    return [unbent(piece) for piece in pieces if len(piece) > 1]


def unbent(points):
    """The polyline without the points that lie on the straight between their neighbours: the
    band's edges leave one on any leg they cross, and a leg in two boxes is a joint for nothing."""
    kept = [points[0]]
    for (x0, z0), (x1, z1), (x2, z2) in zip(points, points[1:], points[2:]):
        if abs((x1 - x0) * (z2 - z1) - (z1 - z0) * (x2 - x1)) > 1e-6:
            kept.append((x1, z1))
    kept.append(points[-1])
    return kept


def frog(line):
    """The branch's rail cut around whichever main-line rail it crosses."""
    pieces = [line]
    for z in RAIL_CENTRES:
        pieces = [part for piece in pieces for part in cut_across(piece, z - 1 - FROG, z + 1 + FROG)]
    return pieces


def junction(left):
    """The main line east to west, and the branch: a curve from the north face onto the main
    line, swinging to the left of a cart coming down the stem or to the right.

    That is what the block does - a cart down the stem curves onto the line - so it is what the
    block shows: a curve laid over a straight, the way a real switch is. The curve is the same
    quarter turn every bend uses, mirrored to face the right wall and set a hair lower so the
    straight's own rails win where the two run together. Where the branch's outer rail crosses
    the main line's near rail it stops short either side, the way a frog has a gap for the
    flange, rather than running underneath as if the two were laid through each other.
    """
    elements = []
    for x in TIE_CENTRES:
        elements.append(straight_tie(x, 1, 15, "z"))
    for column, z in zip(RAILS, RAIL_CENTRES):
        elements.append(straight_rail(column, z, 0, 16, "x"))
    # Coming down the stem means heading south, so left is east.
    place = (lambda p: (p[0], 16 - p[1])) if left else (lambda p: (16 - p[0], 16 - p[1]))
    # Rails only: a switch shares the straight's sleepers, and a second set laid across them at
    # odd angles read as a bundle of sticks rather than a track.
    elements += curve_elements(place, TIE_HEIGHT - UNDER, TIE_HEIGHT - UNDER,
                               TIE_HEIGHT + RAIL_HEIGHT - UNDER, sleepers=False, cut=frog)
    return {"ambientocclusion": False, "textures": {"particle": "#rail", "rail": "minecraft:block/rail"},
            "elements": elements}


# Where a diagonal meets a straight. A chord's rails cross the block face two pixels outboard
# of a straight's, heading forty-five degrees off it, and no single block can bend that back:
# a turn inside the chord's block would miss the face. The straight beyond has sixteen pixels
# to do it in, so the first straight after a diagonal eases each rail from where the chord
# left it onto its own line - the near rail with a short bend and a long run, the far rail
# with a bend out, a shimmy back and a long run - and the two lines meet without a jog.
#
# Where a diagonal meets a straight the two share one bend. An arc tangent to the straight's
# centre line EASE_TANGENT pixels short of the shared face and to the chord the same distance
# past it, drawn as the three legs a model can turn through - 0, 22.5 and 45 degrees - which is
# the polygon wrapped round that arc. The middle leg straddles the face, so the straight draws
# the part on its side and the run's last chord draws the rest, and both rails follow the same
# centre at the same distance, the way rails do. The straight used to take the whole bend on
# its own, from where the chord's rails crossed the face to where its own ran, and the outer
# rail had to swing out past the block and back again to manage it.
EASE_TANGENT = 8.0


def ease_line():
    """The centre line in the straight's pixels: in through its north face, round the bend, and
    out through the west face of the chord block south of it, which lies at z 16 to 32."""
    import math
    t = EASE_TANGENT
    corner = t * math.tan(math.radians(11.25)) / math.tan(math.radians(22.5))
    s, c = math.sin(math.radians(22.5)), math.cos(math.radians(22.5))
    p1 = (8.0, 16 - t + corner)
    p2 = (p1[0] - 2 * corner * s, p1[1] + 2 * corner * c)
    return [(8.0, 0.0), p1, p2, (0.0, 24.0)]


def to_face(points, at_end, axis, value):
    """The polyline with its first or last point slid along its own segment onto a face."""
    points = list(points)
    (x0, z0), (x1, z1) = (points[-2], points[-1]) if at_end else (points[1], points[0])
    dx, dz = x1 - x0, z1 - z0
    u = (value - x0) / dx if axis == "x" else (value - z0) / dz
    points[-1 if at_end else 0] = (x0 + dx * u, z0 + dz * u)
    return points


def split_at(points, z):
    """The polyline in two at z, the crossing point ending one and starting the other."""
    before, after = [], []
    for (x0, z0), (x1, z1) in zip(points, points[1:]):
        if not before:
            before.append((x0, z0))
        if z0 < z < z1:
            u = (z - z0) / (z1 - z0)
            crossing = (x0 + (x1 - x0) * u, float(z))
            before.append(crossing)
            after.append(crossing)
        (after if z1 > z else before).append((x1, z1))
    return before, after


def ease_rails():
    """Each rail's line through both blocks, in the straight's pixels, ending on the faces."""
    lines = []
    for side in (-1, 1):
        line = offset_line(ease_line(), side * RAIL_OFFSET)
        line = to_face(line, False, "z", 0)
        line = to_face(line, True, "x", 0)
        lines.append(line)
    return lines


def rail_boxes(points, column, place=lambda p: p):
    elements = []
    for p0, p1 in zip(points, points[1:]):
        length = ((p1[0] - p0[0]) ** 2 + (p1[1] - p0[1]) ** 2) ** 0.5
        elements.append(box_between(place(p0), place(p1), 2, TIE_HEIGHT, TIE_HEIGHT + RAIL_HEIGHT,
                                    rail_faces(column, length), "z"))
    return elements


# Drawn for a north-south straight receiving a diagonal through its south face heading
# north-east; mirrored for north-west, and turned by the blockstate for the other faces.
def easing(mirror):
    place = (lambda p: (16 - p[0], p[1])) if mirror else (lambda p: p)
    elements = [straight_tie(z, 1, 15, "x") for z in TIE_CENTRES]
    for column, line in zip(RAILS, ease_rails()):
        elements += rail_boxes(split_at(line, 16)[0], column, place)
    return {"ambientocclusion": False, "textures": {"particle": "#rail"}, "elements": elements}


# The last chord of a run, eased where it meets the straight. Drawn for the north-west curve
# with the straight beyond its north face, then mirrored and turned for the other seven.
END_TIE_AT = (2.0, 5.2, 8.2)   # from the west face along the centre line; the last sits on the
                              # bend, and any further along it lies across the straight's own tie


def end_chord(place):
    into_d = lambda p: (p[0], p[1] - 16)
    centre = [into_d(p) for p in reversed(split_at(ease_line(), 16)[1])]
    centre = to_face(centre, False, "x", 0)
    elements = []
    for distance in END_TIE_AT:
        (x, z), (dx, dz) = along(centre, distance)
        nx, nz = dz, -dx
        half = CHORD_TIE_HALF
        elements.append(box_between(place((x - nx * half, z - nz * half)), place((x + nx * half, z + nz * half)),
                                    2, 0, TIE_HEIGHT, tie_faces(2 * half)))
    for column, line in zip(RAILS, ease_rails()):
        elements += rail_boxes([into_d(p) for p in split_at(line, 16)[1]], column, place)
    return {"ambientocclusion": False, "textures": {"particle": "#rail"}, "elements": elements}


def turned_cw(p):
    return (16 - p[1], p[0])


def mirrored(p):
    return (16 - p[0], p[1])


def compose(*fs):
    def run(p):
        for f in fs:
            p = f(p)
        return p
    return run


# Curve and the face its straight is beyond, to the transform that lays the drawn one there.
END_CHORDS = {}
for i, (curve, face) in enumerate((("nw", "n"), ("ne", "e"), ("se", "s"), ("sw", "w"))):
    END_CHORDS[(curve, face)] = compose(*([turned_cw] * i))
for i, (curve, face) in enumerate((("ne", "n"), ("se", "e"), ("sw", "s"), ("nw", "w"))):
    END_CHORDS[(curve, face)] = compose(mirrored, *([turned_cw] * i))


def diagonals(directory, name, texture):
    """Four chords under <name>_diagonal_<curve>, eight run ends under
    <name>_diagonal_<curve>_<face> and two easings under <name>_diagonal_end_<hand>, all
    pointing at <texture>."""
    for curve, (cx, cz, angle) in DIAGONALS.items():
        model = diagonal(cx, cz, angle)
        model["textures"]["rail"] = texture
        write(os.path.join(directory, "models/block", name + "_diagonal_" + curve + ".json"), model)
    for (curve, face), place in END_CHORDS.items():
        model = end_chord(place)
        model["textures"]["rail"] = texture
        write(os.path.join(directory, "models/block", name + "_diagonal_" + curve + "_" + face + ".json"), model)
    for hand, mirror in (("ne", False), ("nw", True)):
        model = easing(mirror)
        model["textures"]["rail"] = texture
        write(os.path.join(directory, "models/block", name + "_diagonal_end_" + hand + ".json"), model)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", os.path.relpath(path, HERE))


def model(name, parent, texture):
    write(os.path.join(MOD, "models/block", name + ".json"),
          {"parent": parent, "textures": {"rail": NS + ":block/" + texture}})


def item(name):
    write(os.path.join(MOD, "models/item", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + name}})
    write(os.path.join(MOD, "items", name + ".json"),
          {"model": {"type": "minecraft:model", "model": NS + ":item/" + name}})


def rail_variants(name, corner):
    """Vanilla's rail blockstate, pointed at this rail's models."""
    m = lambda suffix: NS + ":block/" + name + suffix
    return {
        "shape=north_south": {"model": m("")},
        "shape=east_west": {"model": m(""), "y": 90},
        "shape=ascending_north": {"model": m("_raised_ne")},
        "shape=ascending_east": {"model": m("_raised_ne"), "y": 90},
        "shape=ascending_south": {"model": m("_raised_sw")},
        "shape=ascending_west": {"model": m("_raised_sw"), "y": 90},
        **({"shape=south_east": {"model": m("_corner")},
            "shape=south_west": {"model": m("_corner"), "y": 90},
            "shape=north_west": {"model": m("_corner"), "y": 180},
            "shape=north_east": {"model": m("_corner"), "y": 270}} if corner else {}),
    }


def main():
    # The 3D templates, under vanilla's own names
    write(os.path.join(VANILLA, "models/block/rail_flat.json"), flat())
    write(os.path.join(VANILLA, "models/block/template_rail_raised_ne.json"), raised(45))
    write(os.path.join(VANILLA, "models/block/template_rail_raised_sw.json"), raised(-45))
    # The curve is boxes now too, cut from the straight texture: the corner texture is a
    # picture of a bend, and a bend built out of straight pieces wants straight pixels.
    write(os.path.join(VANILLA, "models/block/rail_curved.json"), curved())
    write(os.path.join(VANILLA, "models/block/rail_corner.json"),
          {"parent": "minecraft:block/rail_curved", "textures": {"rail": "minecraft:block/rail"}})

    # Wooden rail: every shape the iron rail has
    model("wooden_rail", "minecraft:block/rail_flat", "wooden_rail")
    model("wooden_rail_corner", "minecraft:block/rail_curved", "wooden_rail")
    model("wooden_rail_raised_ne", "minecraft:block/template_rail_raised_ne", "wooden_rail")
    model("wooden_rail_raised_sw", "minecraft:block/template_rail_raised_sw", "wooden_rail")
    write(os.path.join(MOD, "blockstates/wooden_rail.json"), {"variants": rail_variants("wooden_rail", True)})

    # Copper powered rail: straight and sloped, lit and unlit, like gold's
    variants = {}
    for powered, tex in (("false", "copper_powered_rail"), ("true", "copper_powered_rail_on")):
        base = "copper_powered_rail" + ("_on" if powered == "true" else "")
        model(base, "minecraft:block/rail_flat", tex)
        model(base + "_raised_ne", "minecraft:block/template_rail_raised_ne", tex)
        model(base + "_raised_sw", "minecraft:block/template_rail_raised_sw", tex)
        for key, value in rail_variants(base, False).items():
            variants["powered=" + powered + "," + key] = value
    write(os.path.join(MOD, "blockstates/copper_powered_rail.json"), {"variants": variants})

    # Crossing: two lines through one block, both meaning "straight through"
    write(os.path.join(MOD, "models/block/cross_rail.json"), crossing())
    write(os.path.join(MOD, "blockstates/cross_rail.json"), {"variants": {
        "shape=north_south": {"model": NS + ":block/cross_rail"},
        "shape=east_west": {"model": NS + ":block/cross_rail"}}})

    # Junction: drawn by where the stem points and which way the branch swings; the shape it
    # is holding is invisible
    write(os.path.join(MOD, "models/block/tee_rail_left.json"), junction(True))
    write(os.path.join(MOD, "models/block/tee_rail_right.json"), junction(False))
    tee = {}
    for stem, turn in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for hand in ("left", "right"):
            variant = {"model": NS + ":block/tee_rail_" + hand}
            if turn:
                variant["y"] = turn
            tee["stem=" + stem + ",left=" + str(hand == "left").lower()] = variant
    write(os.path.join(MOD, "blockstates/tee_rail.json"), {"variants": tee})

    # The chords, for vanilla's rails and this mod's
    for name in ("rail", "powered_rail", "powered_rail_on", "detector_rail", "detector_rail_on", "activator_rail", "activator_rail_on"):
        diagonals(VANILLA, name, "minecraft:block/" + name)
    diagonals(MOD, "wooden_rail", NS + ":block/wooden_rail")

    for name in ("wooden_rail", "copper_powered_rail", "cross_rail", "tee_rail", "dropper_minecart", "dispenser_minecart"):
        item(name)


if __name__ == "__main__":
    main()
