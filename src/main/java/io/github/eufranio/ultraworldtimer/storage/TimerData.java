package io.github.eufranio.ultraworldtimer.storage;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.misc.BaseDaoEnabled;
import com.j256.ormlite.table.DatabaseTable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.time.Instant;
import java.util.UUID;

/**
 * Created by Frani on 25/02/2019.
 */
@DatabaseTable(tableName = "timers")
public class TimerData extends BaseDaoEnabled<TimerData, UUID> {

    @DatabaseField(generatedId = true, index = true)
    public UUID uuid;

    @DatabaseField(dataType = DataType.SERIALIZABLE)
    public Instant entered;

    @DatabaseField
    public int timer; // in seconds

    @DatabaseField
    public String location;

    public void setLocation(Location<World> loc) {
        this.location = loc.getX() + "," + loc.getY() + "," + loc.getX() + "," + loc.getExtent().getName();
    }

    public Location<World> getLocation() {
        String[] arr = this.location.split(",");
        World world = Sponge.getServer().getWorld(arr[3]).get();
        return new Location<>(world, Double.valueOf(arr[0]), Double.valueOf(arr[1]), Double.valueOf(arr[2]));
    }


}
