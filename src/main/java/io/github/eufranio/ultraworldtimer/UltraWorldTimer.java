package io.github.eufranio.ultraworldtimer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.pixelmonmod.pixelmon.entities.EntityWormhole;
import com.pixelmonmod.pixelmon.worldGeneration.dimension.ultraspace.UltraSpace;
import com.pixelmonmod.pixelmon.worldGeneration.dimension.ultraspace.UltraSpaceTeleporter;
import io.github.eufranio.ultraworldtimer.storage.Persistable;
import io.github.eufranio.ultraworldtimer.storage.TimerData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.CollideEntityEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "ultraworldtimer",
        name = "UltraWorldTimer",
        description = "Adds a timer for players in the ultra world",
        authors = {
                "Eufranio"
        }
)
public class UltraWorldTimer {

    @Inject
    private Logger logger;

    private Persistable<TimerData> timers;
    private Map<UUID, Task> tasks = Collections.synchronizedMap(Maps.newHashMap());

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        this.timers = Persistable.create(TimerData.class, "jdbc:sqlite:UltraWorldTimer.db", true);
        Task.builder()
                .interval(1, TimeUnit.MINUTES)
                .async()
                .execute(() -> {
                    Instant now = Instant.now();
                    List<TimerData> toDelete = Lists.newArrayList();
                    for (TimerData data : this.timers.getAll(true)) {
                        if (this.tasks.containsKey(data.uuid)) continue;
                        if (data.entered.plusSeconds(data.timer).isBefore(now)) {
                            UUID uuid = data.uuid;
                            Location<World> loc = data.getLocation();
                            Task.builder().execute(() -> this.teleportBack(uuid, loc)).submit(this);
                            toDelete.add(data);
                        }
                    }
                    toDelete.forEach(this.timers::delete);
                })
                .submit(this);
    }

    @Listener
    public void onCollide(CollideEntityEvent e, @Root Player player) {
        e.getEntities().forEach(entity -> {
            if (entity instanceof EntityWormhole) {
                if (((EntityPlayerMP) player).dimension != UltraSpace.DIM_ID) {
                    Location<World> loc = player.getLocation();
                    int time = player.getOption("uwt.time").map(Integer::parseInt).orElse(60);
                    UUID uuid = player.getUniqueId();

                    TimerData data = new TimerData();
                    data.uuid = uuid;
                    data.timer = time;
                    data.entered = Instant.now();
                    data.setLocation(loc);
                    this.timers.save(data);
                    if (tasks.containsKey(player.getUniqueId())) {
                        Task t = this.tasks.remove(player.getUniqueId());
                        if (t != null) t.cancel();
                    }

                    tasks.put(player.getUniqueId(),
                            Task.builder()
                            .delay(time, TimeUnit.SECONDS)
                            .execute(() -> {
                                this.teleportBack(uuid, loc);
                                this.tasks.remove(uuid).cancel();
                            })
                            .submit(this)
                    );
                } else {
                    Task task = this.tasks.get(player.getUniqueId());
                    if (task != null) task.cancel();
                    this.timers.get(player.getUniqueId()).ifPresent(this.timers::delete);
                }
            }
        });
    }

    private void teleportBack(UUID uuid, Location<World> loc) {
        User user = Sponge.getServer().getPlayer(uuid).map(User.class::cast).orElseGet(() -> {
            UserStorageService svc = Sponge.getServiceManager().provideUnchecked(UserStorageService.class);
            return svc.get(uuid).orElse(null);
        });
        if (user == null) return;
        if (user.isOnline()) {
            user.getPlayer().get().setLocation(loc);
        } else {
            user.setLocation(loc.getPosition(), loc.getExtent().getUniqueId());
        }
    }
}

