package activitystreamer.util;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class CommonUtil {
    public static Object getMinKey(Map<String, JsonObject> map) {
        if (map == null) {
            return null;
        }
        Set<String> set = map.keySet();
        Object[] objs = set.toArray();
        Arrays.sort(objs);
        return objs[0];
    }

    public static Object getMinValue(Map<Integer, Integer> map) {
        if (map == null) {
            return null;
        }
        Collection<Integer> c = map.values();
        Object[] objs = c.toArray();
        Arrays.sort(objs);
        return objs[0];
    }
}
