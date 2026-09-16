# Minecart Mania

A Fabric mod that makes the railway worth building: faster track that switches itself, a
handbrake, chained carts, carts that lay track and dig, and a TNT cart that bores a tunnel.

## What This Mod Does

Vanilla's railway is one speed, one bend, and carts that do nothing but carry. This puts the
speed in the track, gives the rider a lever, lets carts be coupled into trains, and turns three
of the working carts into tools.

Everything runs on the game's new minecart movement, which this mod switches on for every
world. That is the only physics that can go past eight metres a second.

## Rails

**The track sets the pace.** A cart goes as fast as the rail under it allows:

| Rail | Made of | Top speed |
|---|---|---|
| Wooden rail | planks | 8 m/s, vanilla's |
| Rail | iron | 12 m/s |
| Copper powered rail | waxed copper | 16 m/s, with power |
| Powered rail | gold | 32 m/s, with power |

Detector rails, activator rails, the crossing and the junction are iron and run at iron's pace.
Powered rails still need redstone to push; without it they brake, as gold does.

**Rail Crossing.** Two lines cross at grade. A cart on either goes straight through. The
crossing sets itself to whichever line a cart arrives on, so two lines never have to agree.

**Rail Junction.** A main line with a branch that merges onto it. A cart down the branch curves
onto the main line, to the left of its travel or the right; a cart along the main line goes
straight and never turns up the branch. Place it standing on the branch, facing the main line.
Shift-click it to flip which way the branch merges, or power it: a signal arriving swings the
branch to the other side and a signal leaving swings it back, the way redstone works a fork of
vanilla curves. It is drawn as a switch: the branch's rails laid over the main line's, with the
frog's gap where the branch crosses the near rail.

**Shift-click a rail** with an empty hand to step it through the shapes it could take. Only
shapes whose both ends meet a rail are offered, and the one you choose is pinned: the next rail
laid beside it cannot bend it back. A rail with redstone on it belongs to the redstone. This is
the cure for a rail that insists on curving toward the wrong neighbour.

**Rails stand on nothing.** Bridges, trestles and spans over a ravine need no block under every
sleeper.

**Rails have depth.** Every straight and sloped rail is drawn as sleepers with two rails standing
on them, vanilla's included, and a curve is a quarter turn of short straights with the rails
mitred at every joint. A run of curves, the staircase that is a diagonal line of track, is drawn as
the straight diagonal it stands for on Pandorical clients, end to end from the straight it leaves to
the straight it reaches, and where one diagonal turns into another the two lines meet at a corner;
the cart still takes the steps. Where a diagonal meets a straight the two share one bend, half in
each block, so both rails swing round together. A curve on its own between two straights is a
corner and keeps its bend.

**Recipes give twice vanilla's count**: thirty-two rails, twelve each of powered, detector and
activator rails, two carts. Wooden rails are thirty-two from planks around a stick; copper
powered rails are twelve from waxed copper blocks around a stick and redstone. The crossing is
four rails around an iron ingot; the junction is three rails over an ingot over a rail; each
makes two.

## Carts

**Speed hurts.** Above six metres a second a cart hurts and throws whatever stands in its way,
harder the faster it goes, and past sixteen metres a second it keeps every bit of its momentum:
a cart on a gold line does not notice a zombie.

**The handbrake.** Hold the Minecart Handbrake key, B by default, while riding, and the cart
sheds speed until you let go, sparking and grinding as it does. Pandorical carries the key; a
vanilla client has no lever.

**Chains.** Chain in hand, click one cart and then another, and they are linked: the chain is
spent, the second cart snaps to the first's speed, and from then on they hold a little over a
cart's length apart, one pulling or pushing the other, with a gap of air kept between them.
Each cart takes a chain at either end, so click on down the line and it is a train. Chained
carts pass through each other rather than bouncing, and neither can block the other. The chain
hangs between them, under the carts, for Pandorical clients. A cart with a chain held to it and
nothing on the other end is on a leash: walk off with the chain in hand and it comes along after
you at a walk, until you chain it to something or get too far away. Sneak and click a
cart with a chain and it comes free of everything it was chained to, the chains dropping at
your feet; break a chained cart and its chains drop with it.

**The furnace cart** has controls: an empty hand on it opens a panel with a four-notch
throttle, a Turn around button and a switch that reads Running or Stopped. Notch four is the
full pace of the rail it is on, where vanilla's furnace cart managed three quarters of a plain
cart; each notch below takes a quarter off, and each notch above one burns fuel that much
faster. Turn around flips the cart on the spot, push and motion both, and the panel shows the
compass heading it is going or facing and how long its fire has left. A powered activator rail
throws the switch, so a station can stop a train and start it again. The panel has a fuel slot
under a furnace's flame: what is put in the slot stays there, and the cart takes one from it
when the fire goes out and it is running, never while it is stopped. Anything a furnace burns
will do, there or fed by hand, for the time it would burn in a furnace scaled the way vanilla
scales coal for a cart; vanilla took coal and nothing else. Feeding it by hand no longer turns
it: a furnace cart pushes the way its arrow points.

**Arrows.** An arrow lies flat over every furnace cart and working cart: the furnace's points
the way it pushes, a dropper's or dispenser's the way it lays or throws, so a train reads at a
glance. It is an item display riding the cart, so vanilla clients see it too. A cart set down by
a player faces the way they are looking, along the rail.

**The working carts** are a dropper or a dispenser put together with a minecart on the crafting
grid, and are set on a rail like any cart. The panel sets the side, front, back, left or right,
and how often, every one to sixty-four blocks of travel, counted by where the cart stands and not
by speed, so a parked cart jostled by its chain stays put. A new dropper cart works forward every
block, a new dispenser cart forward every four. With block-tip installed, its tip names them as
what they are rather than as chest carts.

**The dropper cart** lays what it carries. A dropper in a cart holds twenty-seven slots and,
every so many blocks of travel, places one of the first thing in them to its front, back, left
or right; a thing that is not a block is dropped there instead. Forward is the interesting one:
rails laid one block ahead of a moving cart are a track that builds itself; a torch to the left
every eight blocks is a lit tunnel. Laid rail keeps its line: a level run spans a drop, a
falling run keeps falling, a climb goes on while there is ground to climb and levels off onto
the plateau when there is not, and a wall ahead with room over it starts one. Track already
there is driven over, not built on, a curve underfoot followed round, and a rail cart anywhere
in a train looks past the carts ahead of it, up to eight blocks, for where the line ends.
Right-click it for the slots and the controls; sneak-click for the bare chest. Break it and it
comes back as itself.

**The dispenser cart** throws what it carries, every so many blocks of travel, in one of the
four directions. What it throws best is TNT: lobbed ahead and up, lit short, and burst as a
mining charge that spares the level of the rail it was thrown from and everything below,
hurts nobody, and drops every block it takes. A dropper cart laying rail behind a dispenser
cart lobbing TNT is a boring machine.

**The TNT cart** bores, and only when lit: an activator rail or fire sets it off, and hitting a
wall at speed does not, so a charge in a train or on a chain is a charge and not a fuse. Lit, it
stops where it is, tips its nose down over the fuse, and then
puts the whole charge forward: a tunnel three wide, three high and up to sixteen long from the
rail it stopped on, floor kept, every block dropped, nothing standing near it hurt. How long is
how fast it was going when it was lit: the full sixteen at the pace of the rail under it, two
blocks from a standstill. The cart is spent, bar a handful of iron nuggets on the floor.

**A TNT train** sent over a powered activator rail goes off as one charge. Up to six TNT carts
answer, the chain walked from the lead and stopping at the first cart that is not TNT; how many
of those come depends on the pace they arrived at, from the lead alone at a crawl to all six at
the rail's full speed. The rest of the train is unhooked and stops where it stands, and a
furnace cart pushing it is set to Stopped. Each cart in the volley is flung to a spot of its own
against the face, nose to the face, and they go off together on the lead's fuse: the bore is a
block wider and higher and four deeper for each cart past the first, up to six wide, then eight
deeper for each cart after that, the floor kept. The leavings are pooled, the chains between the carts going in
as a nugget or two each, and some are forged back into ingots: one per cart past the first,
and as many again on a good roll.

**Carts come apart.** Any cart with a block in it splits on the crafting grid: the block is the
result and the bare cart comes back as the crafting remainder, the way a bucket does. That
covers the chest, furnace, hopper and TNT carts and both working carts.

## Standing On Rails

A rail holds a player up. Every rail, floating or not: a flat one is two pixels of deck, a
sloped one a ramp of eight steps, so a bridge of rail is a bridge you can walk across and a
trestle is a thing you can stand on to build the next span. Only players: carts, mobs and
dropped items pass through rails exactly as they do in vanilla, so nothing about how a cart
rides changes. This needs Pandorical on the client, because a client has to predict the same
floor the server holds you up with.

## Pandorical

Minecart Mania registers its rails and carts through Pandorical's content sync, draws the
chain through it, takes the handbrake key from it, builds the furnace and working cart panels
with it, and tells it rails are solid. **Pandorical is required on the server.**

**The Pandorical mod must be installed client-side** for the rails and carts to look like
themselves, for the handbrake and the chain. Without it the mod still works
server-side: rails run at their speeds, junctions switch, carts dig and lay and hurt, but a
vanilla client sees untextured rails, an iron cart, and has no brake.

## Development

Installing and the art pipeline are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
