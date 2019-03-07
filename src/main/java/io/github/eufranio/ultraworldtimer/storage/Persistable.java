package io.github.eufranio.ultraworldtimer.storage;

import com.google.common.collect.Lists;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.misc.BaseDaoEnabled;
import com.j256.ormlite.table.TableUtils;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Created by Frani on 04/12/2018.
 */
public class Persistable<T extends BaseDaoEnabled<T, UUID>> {

    private Dao<T, UUID> objDao;
    private static JdbcConnectionSource src;
    private boolean lazyLoad = false;
    private List<T> contents = Lists.newArrayList();

    private Persistable(String url, boolean lazyLoad, Class<T> clazz) {
        try {
            this.lazyLoad = lazyLoad;
            if (src == null) src = new JdbcConnectionSource(url);
            this.objDao = DaoManager.createDao(src, clazz);
            TableUtils.createTableIfNotExists(src, clazz);
            if (!this.lazyLoad) this.populate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void populate() {
        try {
            this.contents.addAll(this.objDao.queryForAll());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void repopulate() {
        this.contents.clear();
        populate();
    }

    public void save(T obj) {
        try {
            if (obj.getDao() == null) {
                obj.setDao(this.objDao);
            }
            this.objDao.createOrUpdate(obj);
            if (!contents.contains(obj)) contents.add(obj);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(T obj) {
        try {
            this.objDao.delete(obj);
            this.contents.remove(obj);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Optional<T> get(UUID uuid) {
        try {
            return Optional.ofNullable(this.objDao.queryForId(uuid));
        } catch (SQLException e) { e.printStackTrace(); }
        return Optional.empty();
    }

    public T getOrCreate(UUID id) {
        try {
            T obj = this.contents.stream().filter(o -> {
                try {
                    return o.extractId().equals(id);
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
            }).findFirst().orElse(null);
            if (obj == null) {
                obj = this.objDao.queryForId(id);
                if (obj == null) {
                    obj = this.objDao.getDataClass().newInstance();
                    Field f = obj.getClass().getDeclaredField("uuid");
                    f.set(obj, id);
                    this.save(obj);
                }
            }
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<T> getAll() {
        return Lists.newArrayList(this.contents);
    }

    public List<T> getAll(boolean load) {
        try {
            return this.objDao.queryForAll();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T extends BaseDaoEnabled<T, UUID>> Persistable<T> create(Class<T> clazz, String url, boolean lazyLoad) {
        return new Persistable<>(url, lazyLoad, clazz);
    }

}