package twilightmoonshine.moonlight;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * 暮色森林维度的"月光"开关状态，随世界存档持久化。
 */
public class MoonlightWorldData extends SavedData {

    private static final String NAME = "twilightmoonshine_moonlight";

    private static final Factory<MoonlightWorldData> FACTORY = new Factory<>(
        MoonlightWorldData::new, MoonlightWorldData::load);

    private boolean active;

    /** 取（或新建）当前维度的月光状态 */
    public static MoonlightWorldData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(FACTORY, NAME);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        setDirty();
    }

    public static MoonlightWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        MoonlightWorldData data = new MoonlightWorldData();
        data.active = tag.getBoolean("active");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("active", active);
        return tag;
    }
}
