package de.robotricker.transportpipes.pipes.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jnbt.CompoundTag;
import org.jnbt.NBTTagType;
import org.jnbt.Tag;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.api.TransportPipesContainer;
import de.robotricker.transportpipes.pipeitems.ItemData;
import de.robotricker.transportpipes.pipeitems.PipeItem;
import de.robotricker.transportpipes.pipes.BlockLoc;
import de.robotricker.transportpipes.pipes.ClickablePipe;
import de.robotricker.transportpipes.pipes.PipeDirection;
import de.robotricker.transportpipes.pipes.PipeType;
import de.robotricker.transportpipes.pipes.PipeUtils;
import de.robotricker.transportpipes.pipes.goldenpipe.GoldenPipeInv;
import de.robotricker.transportpipes.pipeutils.NBTUtils;
import de.robotricker.transportpipes.pipeutils.PipeItemUtils;
import de.robotricker.transportpipes.pipeutils.config.LocConf;

public class GoldenPipe extends Pipe implements ClickablePipe {

	//1st dimension: output dirs in order of PipeDirection.values() | 2nd dimension: output items in this direction
	private ItemData[][] outputItems = new ItemData[6][7];
	private BlockingMode[] blockingModes = new BlockingMode[6];
	private FilteringMode[] filteringModes = new FilteringMode[6];

	public GoldenPipe(Location blockLoc) {
		super(blockLoc);
		for (int i = 0; i < 6; i++) {
			blockingModes[i] = BlockingMode.OPENED;
			filteringModes[i] = FilteringMode.CHECK_TYPE_DAMAGE_NBT;
		}
	}

	@Override
	public PipeDirection calculateNextItemDirection(PipeItem item, PipeDirection before, Collection<PipeDirection> possibleDirs) {
		ItemData itemData = item.getItem();
		List<PipeDirection> possibleDirections = getPossibleDirectionsForItem(itemData, before);
		if (possibleDirections == null) {
			return null;
		}
		return possibleDirections.get(new Random().nextInt(possibleDirections.size()));
	}

	public List<PipeDirection> getPossibleDirectionsForItem(ItemData itemData, PipeDirection before) {
		//all directions in which is an other pipe or inventory-block
		List<PipeDirection> blockConnections = PipeUtils.getOnlyBlockConnections(this);
		List<PipeDirection> pipeConnections = PipeUtils.getOnlyPipeConnections(this);

		//the possible directions in which the item could go
		List<PipeDirection> possibleDirections = new ArrayList<>();
		List<PipeDirection> emptyPossibleDirections = new ArrayList<>();

		Map<BlockLoc, TransportPipesContainer> containerMap = TransportPipes.instance.getContainerMap(blockLoc.getWorld());

		for (int line = 0; line < 6; line++) {
			PipeDirection dir = PipeDirection.fromID(line);
			if (dir.getOpposite() == before) {
				continue;
			}
			if (getBlockingMode(line) == BlockingMode.BLOCKED) {
				continue;
			}
			//ignore the direction in which is no pipe or inv-block
			if (!blockConnections.contains(dir) && !pipeConnections.contains(dir)) {
				continue;
			} else if (blockConnections.contains(dir)) {
				//skip possible connection if the container block next to the golden pipe has no space for this item
				if (containerMap != null) {
					BlockLoc bl = BlockLoc.convertBlockLoc(blockLoc.clone().add(dir.getX(), dir.getY(), dir.getZ()));
					if (containerMap.containsKey(bl)) {
						TransportPipesContainer tpc = containerMap.get(bl);
						if (!tpc.isSpaceForItemAsync(dir.getOpposite(), itemData)) {
							continue;
						}
					}
				}
			}
			boolean empty = true;
			for (int i = 0; i < outputItems[line].length; i++) {
				if (outputItems[line][i] != null) {
					empty = false;
				}
				if (itemData.equals(outputItems[line][i], getFilteringMode(line))) {
					possibleDirections.add(dir);
				}
			}
			if (empty) {
				emptyPossibleDirections.add(dir);
			}
		}

		//drop the item if it can't go anywhere
		if (possibleDirections.isEmpty() && emptyPossibleDirections.isEmpty()) {
			return null;
		}

		//if this item isn't in the list, it will take a random direction from the empty dirs
		if (possibleDirections.isEmpty()) {
			possibleDirections.addAll(emptyPossibleDirections);
		}

		return possibleDirections;
	}

	@Override
	public void saveToNBTTag(HashMap<String, Tag> tags) {
		super.saveToNBTTag(tags);

		for (int line = 0; line < 6; line++) {
			List<Tag> lineList = new ArrayList<>();
			for (int i = 0; i < outputItems[line].length; i++) {
				ItemData itemData = outputItems[line][i];
				if (itemData != null) {
					lineList.add(itemData.toNBTTag());
				}
			}
			NBTUtils.saveListValue(tags, "Line" + line, NBTTagType.TAG_COMPOUND, lineList);
			NBTUtils.saveIntValue(tags, "Line" + line + "_blockingMode", getBlockingMode(line).getId());
			NBTUtils.saveIntValue(tags, "Line" + line + "_filteringMode", getFilteringMode(line).getId());
		}

	}

	@Override
	public void loadFromNBTTag(CompoundTag tag) {
		super.loadFromNBTTag(tag);

		Map<String, Tag> map = tag.getValue();
		for (int line = 0; line < 6; line++) {

			List<Tag> lineList = NBTUtils.readListTag(map.get("Line" + line));
			for (int i = 0; i < outputItems[line].length; i++) {
				if (lineList.size() > i) {
					ItemData itemData = ItemData.fromNBTTag((CompoundTag) lineList.get(i));
					outputItems[line][i] = itemData;
				}
			}

			BlockingMode bm = BlockingMode.fromId(NBTUtils.readIntTag(map.get("Line" + line + "_blockingMode"), BlockingMode.OPENED.getId()));
			setBlockingMode(line, bm);

			FilteringMode fm = FilteringMode.fromId(NBTUtils.readIntTag(map.get("Line" + line + "_filteringMode"), FilteringMode.CHECK_TYPE_DAMAGE_NBT.getId()));
			setFilteringMode(line, fm);

		}

	}

	@Override
	public void click(Player p, PipeDirection side) {
		GoldenPipeInv.updateGoldenPipeInventory(p, this);
	}

	public ItemData[] getOutputItems(PipeDirection pd) {
		return outputItems[pd.getId()];
	}

	public BlockingMode getBlockingMode(int line) {
		return blockingModes[line];
	}

	public void setBlockingMode(int line, BlockingMode blockingMode) {
		blockingModes[line] = blockingMode;
	}

	public FilteringMode getFilteringMode(int line) {
		return filteringModes[line];
	}

	public void setFilteringMode(int line, FilteringMode filteringMode) {
		filteringModes[line] = filteringMode;
	}

	public void changeOutputItems(PipeDirection pd, List<ItemData> items) {
		for (int i = 0; i < outputItems[pd.getId()].length; i++) {
			if (i < items.size()) {
				outputItems[pd.getId()][i] = items.get(i);
			} else {
				outputItems[pd.getId()][i] = null;
			}
		}
	}

	@Override
	public PipeType getPipeType() {
		return PipeType.GOLDEN;
	}

	@Override
	public List<ItemStack> getDroppedItems() {
		List<ItemStack> is = new ArrayList<>();
		is.add(PipeItemUtils.getPipeItem(getPipeType(), null));
		for (int line = 0; line < 6; line++) {
			for (int i = 0; i < outputItems[line].length; i++) {
				if (outputItems[line][i] != null) {
					is.add(outputItems[line][i].toItemStack());
				}
			}
		}
		return is;
	}

	public enum BlockingMode {
		OPENED(LocConf.GOLDENPIPE_BLOCKING_DISABLED),
		BLOCKED(LocConf.GOLDENPIPE_BLOCKING_ENABLED);

		private String locConfKey;

		private BlockingMode(String locConfKey) {
			this.locConfKey = locConfKey;
		}

		public String getLocConfKey() {
			return locConfKey;
		}

		public int getId() {
			return this.ordinal();
		}

		public static BlockingMode fromId(int id) {
			return BlockingMode.values()[id];
		}

		public BlockingMode getNextMode() {
			if (getId() == BlockingMode.values().length - 1) {
				return fromId(0);
			}
			return fromId(getId() + 1);
		}

	}

	public enum FilteringMode {
		CHECK_TYPE(LocConf.GOLDENPIPE_FILTERING_CHECKTYPE),
		CHECK_TYPE_DAMAGE(LocConf.GOLDENPIPE_FILTERING_CHECKTYPEDAMAGE),
		CHECK_TYPE_DAMAGE_NBT(LocConf.GOLDENPIPE_FILTERING_CHECKTYPEDAMAGENBT);

		private String locConfKey;

		private FilteringMode(String locConfKey) {
			this.locConfKey = locConfKey;
		}

		public String getLocConfKey() {
			return locConfKey;
		}

		public int getId() {
			return this.ordinal();
		}

		public static FilteringMode fromId(int id) {
			return FilteringMode.values()[id];
		}

		public FilteringMode getNextMode() {
			if (getId() == FilteringMode.values().length - 1) {
				return fromId(0);
			}
			return fromId(getId() + 1);
		}

	}

}