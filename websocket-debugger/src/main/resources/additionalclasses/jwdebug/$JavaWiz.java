package jwdebug;
public class $JavaWiz {
    public static boolean recordCondition(boolean value, int conditionId) {
        return value;
    }

    public static int recordArrayAccess(Object array, int index, int arrayAccessId, int dimension) {
        return index;
    }

    public static void traceStream(String direction, String elem, String name, int id) {
        System.out.println("Test " + direction + " " + elem + " " + name + " " + id);
    }
    public static void traceParam() {}
}