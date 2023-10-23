  import java.util.*;
    import java.util.HashMap;
    import java.util.Iterator;
    import java.util.Map;

    public class IteratMapDemo {

        public static void main(String arg[]) {
            Map<String, String> mapOne = new HashMap<String, String>();
            mapOne.put("1", "January");
            mapOne.put("2", "February");
            mapOne.put("3", "March");
            mapOne.put("4", "April");
            mapOne.put("5", "May");
            mapOne.put("6", "June");
            mapOne.put("7", "July");
            mapOne.put("8", "August");
            mapOne.put("9", "September");
            mapOne.put("10", "Octomber");
            mapOne.put("11", "November");
            mapOne.put("12", "December");

            Iterator it = mapOne.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry me = (Map.Entry) it.next();
                // System.out.println("Get Key through While loop = " + me.getKey());
            }

            for (Map.Entry<String, String> entry:mapOne.entrySet()) {
                // System.out.println(entry.getKey() + "=" + entry.getValue());
            }

            for (Object key : mapOne.keySet()) {
                System.out.println("Key: " + key.toString() + " Value: " +
                                   mapOne.get(key));
            }
        }
    }
